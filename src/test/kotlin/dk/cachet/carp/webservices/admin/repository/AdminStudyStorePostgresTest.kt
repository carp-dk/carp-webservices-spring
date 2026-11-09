package dk.cachet.carp.webservices.admin.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.admin.domain.AdminStudyRow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs [AdminStudyStore]'s aggregate query against real Postgres: recruitment counts, first deployment
 * (staged groups ignored), file counts, the live filter, newest-first ordering and the statement timeout.
 */
@Testcontainers(disabledWithoutDocker = true)
class AdminStudyStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: AdminStudyStore

    private val activeStudy = UUID.randomUUID().stringRepresentation // live, deployed, files
    private val draftStudy = UUID.randomUUID().stringRepresentation // not live, nothing else
    private val owner = UUID.randomUUID().stringRepresentation

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
        store = AdminStudyStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        jdbc.execute("CREATE TABLE recruitments (id INTEGER PRIMARY KEY, snapshot JSONB)")
        dataSource.connection.use { c ->
            ScriptUtils.executeSqlScript(
                c,
                ClassPathResource("db/migration/V8__normalize_recruitment_participants_and_groups.sql"),
            )
        }
        // Minimal stand-ins mirroring V1's relevant columns/types.
        jdbc.execute(
            "CREATE TABLE studies (id SERIAL PRIMARY KEY, created_at TIMESTAMP WITHOUT TIME ZONE, " +
                "updated_at TIMESTAMP WITHOUT TIME ZONE, snapshot JSONB)",
        )
        jdbc.execute("CREATE TABLE files (id SERIAL PRIMARY KEY, study_id VARCHAR(255) NOT NULL)")
        jdbc.update("INSERT INTO recruitments (id) VALUES (1)")
        seed()
    }

    private fun seed() {
        insertStudy(draftStudy, "Draft", isLive = false, createdAt = "2026-01-01 00:00:00")
        insertStudy(
            activeStudy,
            "Active",
            isLive = true,
            createdAt = "2025-06-01 00:00:00",
            applicationData = """{"applicationName":"carp-studies-app"}""",
        )

        listOf("p1", "p2", "p3").forEachIndexed { i, participant ->
            jdbc.update(
                "INSERT INTO recruitment_participants " +
                    "(recruitment_id, study_id, participant_id, account_identity_type, email_address) " +
                    "VALUES (1, ?, ?, 'email', ?)",
                activeStudy,
                participant,
                "p$i@example.com",
            )
        }
        insertGroup(deployment1, isDeployed = true, createdAt = "2025-07-01 09:00:00")
        insertGroup(deployment2, isDeployed = true, createdAt = "2025-08-01 09:00:00")
        // Staged, never deployed: excluded from counts and from firstDeploymentAt even though it is earliest.
        insertGroup(stagedGroup, isDeployed = false, createdAt = "2025-06-15 09:00:00")

        jdbc.update("INSERT INTO files (study_id) VALUES (?), (?)", activeStudy, activeStudy)
    }

    private fun insertStudy(
        studyId: String,
        name: String,
        isLive: Boolean,
        createdAt: String,
        applicationData: String? = null,
    ) {
        val protocol = applicationData?.let { """, "protocolSnapshot": {"applicationData": ${jsonString(it)}}""" } ?: ""
        jdbc.update(
            "INSERT INTO studies (created_at, updated_at, snapshot) " +
                "VALUES (CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), CAST(? AS JSONB))",
            createdAt,
            createdAt,
            """{"id": "$studyId", "name": "$name", "ownerId": "$owner", "isLive": $isLive$protocol}""",
        )
    }

    private fun jsonString(value: String) = "\"" + value.replace("\"", "\\\"") + "\""

    private fun insertGroup(
        groupId: String,
        isDeployed: Boolean,
        createdAt: String,
    ) = jdbc.update(
        "INSERT INTO recruitment_participant_groups (recruitment_id, study_id, group_id, is_deployed, created_at) " +
            "VALUES (1, ?, ?, ?, CAST(? AS TIMESTAMP))",
        activeStudy,
        groupId,
        isDeployed,
        createdAt,
    )

    @Test
    fun `aggregates recruitment, deployment and file figures per study`() {
        val active = store.findAll().single { it.studyId == activeStudy }

        assertEquals(
            AdminStudyRow(
                studyId = activeStudy,
                name = "Active",
                description = null,
                ownerId = owner,
                isLive = true,
                applicationData = """{"applicationName":"carp-studies-app"}""",
                createdAt = Instant.parse("2025-06-01T00:00:00Z"),
                updatedAt = Instant.parse("2025-06-01T00:00:00Z"),
                firstDeploymentAt = Instant.parse("2025-07-01T09:00:00Z"),
                participantCount = 3,
                deploymentCount = 2,
                fileCount = 2,
            ),
            active,
        )
    }

    @Test
    fun `a study with nothing deployed reports zeros and nulls`() {
        val draft = store.findAll().single { it.studyId == draftStudy }

        assertEquals(false, draft.isLive)
        assertNull(draft.firstDeploymentAt)
        assertEquals(listOf(0L, 0L, 0L), with(draft) { listOf(participantCount, deploymentCount, fileCount) })
    }

    @Test
    fun `a query over the statement timeout is cancelled with a clear error`() {
        val impatientStore = AdminStudyStore(jdbc, statementTimeout = Duration.ofSeconds(1))
        // Hold a lock that the listing has to wait for, so it runs past its 1-second timeout.
        val lockHolder = jdbc.dataSource!!.connection
        try {
            lockHolder.autoCommit = false
            lockHolder.createStatement().execute("LOCK TABLE files IN ACCESS EXCLUSIVE MODE")

            val error = assertFailsWith<StatementTimeoutException> { impatientStore.findAll() }
            assertTrue(error.message!!.contains("took longer than 1s"), error.message)
        } finally {
            lockHolder.rollback()
            lockHolder.close()
        }
    }

    @Test
    fun `lists newest first`() {
        assertEquals(listOf(draftStudy, activeStudy), store.findAll().map { it.studyId })
    }

    @Test
    fun `the live filter selects live or non-live studies`() {
        assertEquals(listOf(activeStudy), store.findAll(live = true).map { it.studyId })
        assertEquals(listOf(draftStudy), store.findAll(live = false).map { it.studyId })
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
