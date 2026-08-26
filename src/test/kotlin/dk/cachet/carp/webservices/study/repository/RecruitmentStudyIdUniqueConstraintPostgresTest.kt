package dk.cachet.carp.webservices.study.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifies the V12 unique index that backs [CoreParticipantRepository.addRecruitment]'s check-then-insert
 * guard: two recruitments for the SAME study must be rejected at the database level - Spring Data JPA
 * translates the underlying constraint violation into a DataIntegrityViolationException, already mapped to
 * 409 by ExceptionAdvices.handleConflict with no application code needed - while different studies remain
 * unaffected. Also verifies the migration's own dedup step, which has to run before that index can be
 * created on a table that may already hold pre-existing duplicates (see the migration's comment).
 */
@Testcontainers(disabledWithoutDocker = true)
class RecruitmentStudyIdUniqueConstraintPostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun setup() {
        dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName(postgres.driverClassName)
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }
        jdbc = JdbcTemplate(dataSource)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        jdbc.execute("CREATE TABLE recruitments (id SERIAL PRIMARY KEY, snapshot JSONB)")
        // Mirrors the tables V8 always creates before V12 ever runs (Flyway applies migrations in strict
        // order), which V12's dedup step now reads from - a real deployment never runs V12 without these.
        val childTableColumns = "(recruitment_id INTEGER NOT NULL REFERENCES recruitments (id))"
        jdbc.execute("CREATE TABLE recruitment_participants $childTableColumns")
        jdbc.execute("CREATE TABLE recruitment_participant_groups $childTableColumns")
    }

    private fun runMigration() {
        dataSource.connection.use { c ->
            ScriptUtils.executeSqlScript(
                c,
                ClassPathResource("db/migration/V12__add_recruitment_study_id_unique_constraint.sql"),
            )
        }
    }

    @Test
    fun `rejects a second recruitment for the same study`() {
        runMigration()
        jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb)")

        assertFailsWith<DuplicateKeyException> {
            jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb)")
        }
    }

    @Test
    fun `allows recruitments for different studies`() {
        runMigration()
        jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb)")
        jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-2\"}'::jsonb)")

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM recruitments", Int::class.java))
    }

    @Test
    fun `collapses a pre-existing empty duplicate before creating the index`() {
        // Simulates an environment that already hit the addRecruitment() race this migration guards
        // against: two rows for the same study, inserted before any unique index existed to stop it.
        jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb)")
        jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb)")

        runMigration()

        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruitments WHERE snapshot ->> 'studyId' = 'study-1'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun `leaves a duplicate alone, and fails loudly, if it already carries participant data`() {
        // The one shape the dedup step must NOT silently resolve: the later (higher-id) row of the pair -
        // the only one it will ever consider deleting - already has real participant data attached. The
        // migration must fail loudly here instead of guessing which row to keep.
        jdbc.update("INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb)")
        val laterDuplicateId =
            jdbc.queryForObject(
                "INSERT INTO recruitments (snapshot) VALUES ('{\"studyId\":\"study-1\"}'::jsonb) RETURNING id",
                Int::class.java,
            )
        jdbc.update("INSERT INTO recruitment_participants (recruitment_id) VALUES (?)", laterDuplicateId)

        assertFailsWith<ScriptStatementFailedException> { runMigration() }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
