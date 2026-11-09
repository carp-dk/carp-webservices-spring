package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.jdbc.StatementTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs [StudyDataUploadStore] against real Postgres: per-stream highest-sequence figures over a study's
 * deployed groups (staged groups and NULL sequence ids ignored), study existence, and the statement timeout.
 */
@Testcontainers(disabledWithoutDocker = true)
class StudyDataUploadStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: StudyDataUploadStore

    private val activeStudy = UUID.randomUUID().stringRepresentation // deployed, data uploaded
    private val draftStudy = UUID.randomUUID().stringRepresentation // nothing deployed

    private val deployment1 = UUID.randomUUID().stringRepresentation
    private val deployment2 = UUID.randomUUID().stringRepresentation
    private val stagedGroup = UUID.randomUUID().stringRepresentation

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
        store = StudyDataUploadStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        jdbc.execute("CREATE TABLE recruitments (id INTEGER PRIMARY KEY, snapshot JSONB)")
        dataSource.connection.use { c ->
            ScriptUtils.executeSqlScript(
                c,
                ClassPathResource("db/migration/V8__normalize_recruitment_participants_and_groups.sql"),
            )
        }
        // Minimal stand-ins mirroring the relevant columns/types of V1 and V7.
        jdbc.execute("CREATE TABLE studies (id SERIAL PRIMARY KEY, snapshot JSONB)")
        jdbc.execute(
            "CREATE TABLE data_stream_ids (id INTEGER PRIMARY KEY, study_deployment_id VARCHAR(255) NOT NULL)",
        )
        jdbc.execute(
            "CREATE TABLE data_stream_sequence (id SERIAL PRIMARY KEY, data_stream_id INTEGER, " +
                "first_sequence_id INTEGER, last_sequence_id INTEGER, updated_at TIMESTAMP WITHOUT TIME ZONE)",
        )
        jdbc.update("INSERT INTO recruitments (id) VALUES (1)")
        seed()
    }

    private fun seed() {
        listOf(activeStudy, draftStudy).forEach {
            jdbc.update("INSERT INTO studies (snapshot) VALUES (CAST(? AS JSONB))", """{"id": "$it"}""")
        }
        insertGroup(deployment1, isDeployed = true)
        insertGroup(deployment2, isDeployed = true)
        // Staged, never deployed: its stream must not count.
        insertGroup(stagedGroup, isDeployed = false)

        jdbc.update(
            "INSERT INTO data_stream_ids (id, study_deployment_id) VALUES (1, ?), (2, ?), (3, ?), (4, ?)",
            deployment1,
            deployment1,
            deployment2,
            stagedGroup,
        )
        // Stream 1: sequences 0-9 and 10-24 -> 25 measurements; its highest-sequence row is the latest upload.
        insertSequence(1, 0, 9, "2025-07-02 10:00:00")
        insertSequence(1, 10, 24, "2025-09-01 12:00:00")
        // A row with a NULL last_sequence_id must not win the "highest sequence" pick (NULLs sort first under DESC).
        insertSequence(1, null, null, "2030-01-01 00:00:00")
        // Stream 2: sequences 0-4 -> 5 measurements.
        insertSequence(2, 0, 4, "2025-08-15 08:00:00")
        // Stream 3 exists but has no data yet: counted as a stream, contributes 0 measurements.
        // Stream 4 belongs to the staged group: ignored entirely, however recent.
        insertSequence(4, 0, 99, "2029-01-01 00:00:00")
    }

    private fun insertGroup(
        groupId: String,
        isDeployed: Boolean,
    ) = jdbc.update(
        "INSERT INTO recruitment_participant_groups (recruitment_id, study_id, group_id, is_deployed, created_at) " +
            "VALUES (1, ?, ?, ?, NOW())",
        activeStudy,
        groupId,
        isDeployed,
    )

    private fun insertSequence(
        streamId: Int,
        first: Int?,
        last: Int?,
        updatedAt: String,
    ) = jdbc.update(
        "INSERT INTO data_stream_sequence (data_stream_id, first_sequence_id, last_sequence_id, updated_at) " +
            "VALUES (?, ?, ?, CAST(? AS TIMESTAMP))",
        streamId,
        first,
        last,
        updatedAt,
    )

    @Test
    fun `totals cover the streams of the study's deployed groups`() {
        assertEquals(
            StudyDataUploadRow(
                dataStreamCount = 3,
                measurementCount = 30,
                lastDataUploadAt = Instant.parse("2025-09-01T12:00:00Z"),
            ),
            store.findDataUploads(activeStudy),
        )
    }

    @Test
    fun `a study without deployed groups has no uploads`() {
        assertEquals(StudyDataUploadRow(0, 0, null), store.findDataUploads(draftStudy))
    }

    @Test
    fun `studyExists distinguishes known and unknown studies`() {
        assertTrue(store.studyExists(activeStudy))
        assertFalse(store.studyExists(UUID.randomUUID().stringRepresentation))
    }

    @Test
    fun `a query over the statement timeout is cancelled with a clear error`() {
        val impatientStore = StudyDataUploadStore(jdbc, statementTimeout = Duration.ofSeconds(1))
        // Hold a lock that the query has to wait for, so it runs past its 1-second timeout.
        val lockHolder = jdbc.dataSource!!.connection
        try {
            lockHolder.autoCommit = false
            lockHolder.createStatement().execute("LOCK TABLE data_stream_ids IN ACCESS EXCLUSIVE MODE")

            val error = assertFailsWith<StatementTimeoutException> { impatientStore.findDataUploads(activeStudy) }
            assertTrue(error.message!!.contains("took longer than 1s"), error.message)
        } finally {
            lockHolder.rollback()
            lockHolder.close()
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
