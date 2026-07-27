package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.AfterEach
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
import kotlin.test.assertTrue

/**
 * Exercises [RecruitmentRepositoryImpl.findInactiveDeployments] — the single-aggregate push-down that
 * replaced the per-deployment fan-out — against real Postgres. Verifies the SQL semantics that used to
 * live in Kotlin: only deployed groups, latest-upload (MAX) below the threshold, never-uploaded groups
 * dropped by the inner join, oldest-first ordering, and paging.
 */
@Testcontainers(disabledWithoutDocker = true)
class InactiveDeploymentsPostgresTest {
    private val studyId = UUID.randomUUID()
    private val otherStudyId = UUID.randomUUID()

    // group_id == study-deployment id once deployed.
    private val inactiveOld = UUID.randomUUID() // deployed, last upload 2019 -> inactive (oldest)
    private val inactiveNewer = UUID.randomUUID() // deployed, MAX upload 2020-06 -> inactive
    private val active = UUID.randomUUID() // deployed, last upload 2030 -> active
    private val neverUploaded = UUID.randomUUID() // deployed, no data streams -> excluded
    private val staged = UUID.randomUUID() // not deployed -> excluded

    private val threshold: Instant = Instant.parse("2025-01-01T00:00:00Z")

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager
    private lateinit var repo: RecruitmentRepositoryImpl
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setup() {
        val dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName(postgres.driverClassName)
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }
        jdbc = JdbcTemplate(dataSource)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        jdbc.execute("CREATE TABLE recruitments (id INTEGER PRIMARY KEY, snapshot JSONB)")
        dataSource.connection.use { c ->
            ScriptUtils.executeSqlScript(
                c,
                ClassPathResource("db/migration/V8__normalize_recruitment_participants_and_groups.sql"),
            )
        }
        // Minimal stand-ins for the data-stream tables (mirrors V1's relevant columns/types).
        jdbc.execute(
            "CREATE TABLE data_stream_ids (id INTEGER PRIMARY KEY, study_deployment_id VARCHAR(255) NOT NULL)",
        )
        jdbc.execute(
            "CREATE TABLE data_stream_sequence " +
                "(id INTEGER PRIMARY KEY, data_stream_id INTEGER, updated_at TIMESTAMP WITHOUT TIME ZONE)",
        )
        jdbc.update("INSERT INTO recruitments (id) VALUES (1)")
        seed(jdbc)

        emf =
            Configuration().apply {
                setProperty("hibernate.connection.url", postgres.jdbcUrl)
                setProperty("hibernate.connection.username", postgres.username)
                setProperty("hibernate.connection.password", postgres.password)
                setProperty("hibernate.connection.driver_class", postgres.driverClassName)
                setProperty("hibernate.hbm2ddl.auto", "none")
            }.buildSessionFactory()
        em = emf.createEntityManager()
        repo = RecruitmentRepositoryImpl(em)
    }

    @AfterEach
    fun tearDown() {
        if (this::em.isInitialized) em.close()
        if (this::emf.isInitialized) emf.close()
    }

    @Test
    fun `returns deployed groups whose latest upload predates the threshold, oldest first`() {
        val rows = repo.findInactiveDeployments(studyId.stringRepresentation, threshold, offset = null, limit = null)

        assertEquals(
            listOf(inactiveOld.stringRepresentation, inactiveNewer.stringRepresentation),
            rows.map { it.deploymentId },
            "only the two inactive deployed groups, oldest-upload first",
        )
        // MAX(updated_at) is used: inactiveNewer has uploads in 2020-01 and 2020-06 -> the later one wins.
        val newer = rows.single { it.deploymentId == inactiveNewer.stringRepresentation }.lastDataUpload
        assertTrue(newer.isAfter(Instant.parse("2020-03-01T00:00:00Z")), "expected MAX (2020-06), got $newer")
        assertTrue(newer.isBefore(Instant.parse("2020-09-01T00:00:00Z")), "expected MAX (2020-06), got $newer")
    }

    @Test
    fun `breaks ties on equal timestamps by group_id for stable pagination`() {
        // Two inactive groups with the SAME latest upload; without a tiebreaker their relative order
        // (and thus page boundaries) would be arbitrary between requests.
        val tieA = UUID.randomUUID()
        val tieB = UUID.randomUUID()
        insertGroup(jdbc, tieA, deployed = true)
        insertGroup(jdbc, tieB, deployed = true)
        insertStream(jdbc, 100, tieA)
        insertStream(jdbc, 101, tieB)
        insertSequence(jdbc, 1000, 100, "2021-01-01 00:00:00")
        insertSequence(jdbc, 1001, 101, "2021-01-01 00:00:00")

        // Deterministic expectation: same timestamp -> ordered by group_id ascending.
        val expectedTieOrder =
            listOf(tieA.stringRepresentation, tieB.stringRepresentation).sorted()

        val all = repo.findInactiveDeployments(studyId.stringRepresentation, threshold, offset = null, limit = null)
        val ties = all.map { it.deploymentId }.filter { it in expectedTieOrder }
        assertEquals(expectedTieOrder, ties)

        // Paging one-at-a-time across the tie must return them in that same stable order (no dup/skip).
        val page2 = repo.findInactiveDeployments(studyId.stringRepresentation, threshold, offset = 2, limit = 1)
        val page3 = repo.findInactiveDeployments(studyId.stringRepresentation, threshold, offset = 3, limit = 1)
        assertEquals(listOf(expectedTieOrder[0]), page2.map { it.deploymentId })
        assertEquals(listOf(expectedTieOrder[1]), page3.map { it.deploymentId })
    }

    @Test
    fun `applies limit and offset over the ordered result`() {
        val firstPage = repo.findInactiveDeployments(studyId.stringRepresentation, threshold, offset = 0, limit = 1)
        assertEquals(listOf(inactiveOld.stringRepresentation), firstPage.map { it.deploymentId })

        val secondPage = repo.findInactiveDeployments(studyId.stringRepresentation, threshold, offset = 1, limit = 1)
        assertEquals(listOf(inactiveNewer.stringRepresentation), secondPage.map { it.deploymentId })
    }

    private fun seed(jdbc: JdbcTemplate) {
        insertGroup(jdbc, inactiveOld, deployed = true)
        insertGroup(jdbc, inactiveNewer, deployed = true)
        insertGroup(jdbc, active, deployed = true)
        insertGroup(jdbc, neverUploaded, deployed = true)
        insertGroup(jdbc, staged, deployed = false)

        // streamId -> deployment (group) it belongs to
        insertStream(jdbc, 1, inactiveOld)
        insertStream(jdbc, 2, inactiveNewer)
        insertStream(jdbc, 3, active)
        insertStream(jdbc, 4, staged) // even with data, excluded because the group is not deployed
        // a stream in a different study's deployment must never leak into this study's result
        insertStream(jdbc, 5, otherStudyId)

        insertSequence(jdbc, 10, 1, "2019-01-01 00:00:00")
        insertSequence(jdbc, 20, 2, "2020-01-01 00:00:00")
        insertSequence(jdbc, 21, 2, "2020-06-01 00:00:00") // later upload -> MAX
        insertSequence(jdbc, 30, 3, "2030-01-01 00:00:00") // recent -> active
        insertSequence(jdbc, 40, 4, "2018-01-01 00:00:00")
        insertSequence(jdbc, 50, 5, "2018-01-01 00:00:00")
        // neverUploaded (group) intentionally has no data_stream_ids / sequences.
    }

    private fun insertGroup(
        jdbc: JdbcTemplate,
        groupId: UUID,
        deployed: Boolean,
    ) = jdbc.update(
        "INSERT INTO recruitment_participant_groups (recruitment_id, study_id, group_id, is_deployed, name) " +
            "VALUES (1, ?, ?, ?, ?)",
        studyId.stringRepresentation,
        groupId.stringRepresentation,
        deployed,
        "group",
    )

    private fun insertStream(
        jdbc: JdbcTemplate,
        streamId: Int,
        deploymentId: UUID,
    ) = jdbc.update(
        "INSERT INTO data_stream_ids (id, study_deployment_id) VALUES (?, ?)",
        streamId,
        deploymentId.stringRepresentation,
    )

    private fun insertSequence(
        jdbc: JdbcTemplate,
        id: Int,
        streamId: Int,
        updatedAt: String,
    ) = jdbc.update(
        "INSERT INTO data_stream_sequence (id, data_stream_id, updated_at) VALUES (?, ?, CAST(? AS TIMESTAMP))",
        id,
        streamId,
        updatedAt,
    )

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
