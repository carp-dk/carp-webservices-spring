package dk.cachet.carp.webservices.admin.repository

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.domain.AnnouncementSeverity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Testcontainers(disabledWithoutDocker = true)
class AnnouncementStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: AnnouncementStore

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
        store = AnnouncementStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V13__add_system_announcement.sql"))
        }
    }

    @Test
    fun `find returns null when nothing is set`() {
        assertNull(store.find())
    }

    @Test
    fun `upserting twice keeps a single row holding the latest values`() {
        val first = Instant.parse("2026-11-09T10:00:00.123456Z")
        val second = Instant.parse("2026-11-09T11:30:00Z")

        store.upsert("First", AnnouncementSeverity.INFO, first, "admin-1")
        store.upsert("Second", AnnouncementSeverity.CRITICAL, second, "admin-2")

        assertEquals(Announcement("Second", AnnouncementSeverity.CRITICAL, second, "admin-2"), store.find())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_announcement", Int::class.java))
    }

    @Test
    fun `the stored timestamp round-trips exactly`() {
        val at = Instant.parse("2026-11-09T10:00:00.123456Z")

        store.upsert("Hello", AnnouncementSeverity.INFO, at, "admin")

        assertEquals(at, store.find()?.updatedAt)
    }

    @Test
    fun `delete clears it and is idempotent`() {
        store.upsert("Hello", AnnouncementSeverity.WARNING, Instant.parse("2026-11-09T10:00:00Z"), "admin")

        store.delete()
        store.delete()

        assertNull(store.find())
    }

    @Test
    fun `the table rejects a second row`() {
        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO system_announcement (id, message, severity, updated_at, updated_by) " +
                    "VALUES (2, 'x', 'INFO', now(), 'admin')",
            )
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
