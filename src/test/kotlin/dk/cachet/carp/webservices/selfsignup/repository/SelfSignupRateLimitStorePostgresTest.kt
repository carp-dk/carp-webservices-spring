package dk.cachet.carp.webservices.selfsignup.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.assertEquals

/** Integration coverage for [SelfSignupRateLimitStore]'s per-IP window bucketing and cleanup sweep. */
@Testcontainers(disabledWithoutDocker = true)
class SelfSignupRateLimitStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: SelfSignupRateLimitStore

    @BeforeEach
    fun prepareDatabase() {
        val dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName(postgres.driverClassName)
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }
        jdbc = JdbcTemplate(dataSource)
        store = SelfSignupRateLimitStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V10__add_study_self_signup.sql"),
            )
        }
    }

    @Test
    fun `first increment for an ip and window creates a row with count 1`() {
        val window = Instant.parse("2026-01-01T00:00:00Z")

        assertEquals(1, store.incrementAndGet("10.0.0.1", window))
    }

    @Test
    fun `repeated increments in the same window accumulate on one row`() {
        val window = Instant.parse("2026-01-01T00:00:00Z")

        store.incrementAndGet("10.0.0.1", window)
        store.incrementAndGet("10.0.0.1", window)
        assertEquals(3, store.incrementAndGet("10.0.0.1", window))
    }

    @Test
    fun `distinct ips and distinct windows get distinct counters`() {
        val windowA = Instant.parse("2026-01-01T00:00:00Z")
        val windowB = Instant.parse("2026-01-01T00:01:00Z")

        assertEquals(1, store.incrementAndGet("10.0.0.1", windowA))
        assertEquals(1, store.incrementAndGet("10.0.0.2", windowA))
        assertEquals(1, store.incrementAndGet("10.0.0.1", windowB))
        // The first ip/window pair is unaffected by the other counters.
        assertEquals(2, store.incrementAndGet("10.0.0.1", windowA))
    }

    @Test
    fun `deleteOlderThan removes only windows before the cutoff`() {
        val old = Instant.parse("2026-01-01T00:00:00Z")
        val recent = Instant.parse("2026-01-01T01:00:00Z")
        store.incrementAndGet("10.0.0.1", old)
        store.incrementAndGet("10.0.0.1", recent)

        val deleted = store.deleteOlderThan(Instant.parse("2026-01-01T00:30:00Z"))

        assertEquals(1, deleted)
        assertEquals(
            0L,
            checkNotNull(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM self_signup_rate_limit WHERE window_start = ?",
                    Long::class.java,
                    java.sql.Timestamp.from(old),
                ),
            ),
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
