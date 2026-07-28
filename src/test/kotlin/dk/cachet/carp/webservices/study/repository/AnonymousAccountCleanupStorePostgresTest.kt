package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Integration coverage for [AnonymousAccountCleanupStore.upsert] against a real database: the reset-on-
 * generation semantics (one row per study, GREATEST timer, accumulating count) that Phase 2 cleanup relies on.
 */
@Testcontainers(disabledWithoutDocker = true)
class AnonymousAccountCleanupStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: AnonymousAccountCleanupStore
    private val studyId = UUID.randomUUID().stringRepresentation

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
        store = AnonymousAccountCleanupStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V9__add_anonymous_account_cleanup.sql"),
            )
        }
    }

    @Test
    fun `first upsert inserts a single row`() {
        val deleteAfter = Instant.parse("2026-01-01T00:00:00Z")
        store.upsert(studyId, deleteAfter, 100, Instant.parse("2025-12-01T00:00:00Z"))

        assertEquals(1L, count("SELECT COUNT(*) FROM anonymous_account_cleanup"))
        assertEquals(100L, accountCount())
        assertEquals(deleteAfter, deleteAfter())
    }

    @Test
    fun `a later generation extends the timer and accumulates the count on one row`() {
        val now = Instant.parse("2025-12-01T00:00:00Z")
        store.upsert(studyId, Instant.parse("2026-01-01T00:00:00Z"), 100, now)
        store.upsert(studyId, Instant.parse("2026-02-01T00:00:00Z"), 50, now)

        assertEquals(1L, count("SELECT COUNT(*) FROM anonymous_account_cleanup"))
        assertEquals(150L, accountCount())
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), deleteAfter())
    }

    @Test
    fun `an earlier delete-after does not shrink the timer but still accumulates the count`() {
        val now = Instant.parse("2025-12-01T00:00:00Z")
        store.upsert(studyId, Instant.parse("2026-02-01T00:00:00Z"), 100, now)
        store.upsert(studyId, Instant.parse("2026-01-01T00:00:00Z"), 30, now)

        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), deleteAfter())
        assertEquals(130L, accountCount())
    }

    @Test
    fun `distinct studies get distinct rows`() {
        val other = UUID.randomUUID().stringRepresentation
        val now = Instant.parse("2025-12-01T00:00:00Z")
        store.upsert(studyId, Instant.parse("2026-01-01T00:00:00Z"), 10, now)
        store.upsert(other, Instant.parse("2026-01-01T00:00:00Z"), 20, now)

        assertEquals(2L, count("SELECT COUNT(*) FROM anonymous_account_cleanup"))
    }

    @Test
    fun `findExpired returns only past-due studies, oldest first, capped by limit`() {
        val now = Instant.parse("2026-06-01T00:00:00Z")
        val older = UUID.randomUUID().stringRepresentation
        val newer = UUID.randomUUID().stringRepresentation
        val future = UUID.randomUUID().stringRepresentation
        val written = Instant.parse("2025-12-01T00:00:00Z")
        store.upsert(newer, Instant.parse("2026-05-01T00:00:00Z"), 10, written)
        store.upsert(older, Instant.parse("2026-04-01T00:00:00Z"), 20, written)
        store.upsert(future, Instant.parse("2026-12-01T00:00:00Z"), 5, written)

        val result = store.findExpired(now, 10)
        assertEquals(listOf(older, newer), result.map { it.studyId })
        assertEquals(20L, result.first().accountCount)

        // limit caps the number of rows returned
        assertEquals(1, store.findExpired(now, 1).size)
    }

    @Test
    fun `deleteIfUnchanged removes the row only when delete_after still matches`() {
        val deleteAfter = Instant.parse("2026-01-01T00:00:00Z")
        store.upsert(studyId, deleteAfter, 10, Instant.parse("2025-12-01T00:00:00Z"))

        // A stale delete_after (a concurrent generation extended it) removes nothing.
        assertEquals(0, store.deleteIfUnchanged(studyId, Instant.parse("2025-11-01T00:00:00Z")))
        assertEquals(1L, count("SELECT COUNT(*) FROM anonymous_account_cleanup"))

        // The current delete_after removes the row.
        assertEquals(1, store.deleteIfUnchanged(studyId, deleteAfter))
        assertEquals(0L, count("SELECT COUNT(*) FROM anonymous_account_cleanup"))
    }

    @Test
    fun `markAttempted rotates a study to the back of the queue`() {
        val now = Instant.parse("2025-12-01T00:00:00Z")
        val a = UUID.randomUUID().stringRepresentation
        val b = UUID.randomUUID().stringRepresentation
        // Same delete_after, so ordering is decided purely by last_attempted_at.
        store.upsert(a, Instant.parse("2026-01-01T00:00:00Z"), 10, now)
        store.upsert(b, Instant.parse("2026-01-01T00:00:00Z"), 10, now)
        val scanNow = Instant.parse("2026-06-01T00:00:00Z")

        // Attempt A: it gets a non-null last_attempted_at, so B (still null) now sorts first.
        store.markAttempted(a, now)

        assertEquals(b, store.findExpired(scanNow, 10).first().studyId)
    }

    // ---- helpers -------------------------------------------------------------

    private fun count(sql: String): Long = checkNotNull(jdbc.queryForObject(sql, Long::class.java))

    private fun accountCount(): Long =
        checkNotNull(
            jdbc.queryForObject(
                "SELECT account_count FROM anonymous_account_cleanup WHERE study_id = ?",
                Long::class.java,
                studyId,
            ),
        )

    private fun deleteAfter(): Instant =
        checkNotNull(
            jdbc.queryForObject(
                "SELECT delete_after FROM anonymous_account_cleanup WHERE study_id = ?",
                Timestamp::class.java,
                studyId,
            ),
        ).toInstant()

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
