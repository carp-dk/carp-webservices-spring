package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupRepresentation
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizationStore
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizer
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Exercises the relational participant-search queries of [RecruitmentRepositoryImpl] against a real
 * Postgres with normalized data — the CI regression net for the B1 read path (the one-time real-data
 * equivalence check aside). Seeds via [RecruitmentNormalizationStore] and runs the real repository
 * over a bootstrapped JPA [EntityManager].
 */
@Testcontainers(disabledWithoutDocker = true)
class RecruitmentRepositoryImplPostgresTest {
    private val studyId = UUID.randomUUID()
    private val aliceFirstGroupId = UUID.randomUUID()
    private val aliceLatestGroupId = UUID.randomUUID()
    private val carolGroupId = UUID.randomUUID()
    private lateinit var jdbc: JdbcTemplate
    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager
    private lateinit var repo: RecruitmentRepositoryImpl

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
        jdbc.execute("CREATE TABLE deployments (id SERIAL PRIMARY KEY, snapshot JSONB)")
        dataSource.connection.use { c ->
            ScriptUtils.executeSqlScript(
                c,
                ClassPathResource("db/migration/V8__normalize_recruitment_participants_and_groups.sql"),
            )
        }
        jdbc.update("INSERT INTO recruitments (id) VALUES (1)")
        RecruitmentNormalizationStore(jdbc).replace(1, RecruitmentNormalizer.decompose(seedSnapshot()))
        insertDeployment(aliceFirstGroupId, "2026-01-01T00:00:00Z")
        insertDeployment(aliceLatestGroupId, "2026-03-01T10:20:30.123456Z")
        insertDeployment(carolGroupId, "2026-02-01T00:00:00Z")

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
    fun `count returns all participants in the study`() {
        assertEquals(3, repo.countQueryParticipantAccounts(studyId.stringRepresentation, null, null))
    }

    @Test
    fun `query reconstructs participant json and resolves deployed flags`() {
        val rows =
            repo.queryParticipantAccounts(studyId.stringRepresentation, null, null, null, null, null, null)

        assertEquals(3, rows.size)
        val alice = rows.single { it.participantJson.contains("alice@example.com") }
        assertEquals(true, alice.isDeployed)
        assertNotNull(alice.deploymentId)
        assertContains(alice.participantJson, "EmailAccountIdentity")

        val bob = rows.single { it.participantJson.contains("UsernameAccountIdentity") }
        assertEquals(false, bob.isDeployed) // bob's group is staged, not deployed
        assertNull(bob.deploymentId)
        assertContains(bob.participantJson, "bob")
    }

    @Test
    fun `search matches username and email substrings, case-insensitively`() {
        fun count(search: String) = repo.countQueryParticipantAccounts(studyId.stringRepresentation, search, null)
        assertEquals(1, count("ALICE")) // email local part, case-insensitive
        assertEquals(1, count("bob")) // username
        assertEquals(2, count("example.com")) // both email participants
        assertEquals(0, count("nobody"))
    }

    @Test
    fun `deployed filter selects only participants in a deployed group`() {
        assertEquals(2, repo.countQueryParticipantAccounts(studyId.stringRepresentation, null, true))
        assertEquals(1, repo.countQueryParticipantAccounts(studyId.stringRepresentation, null, false))
    }

    @Test
    fun `query resolves the latest deployment and its invitation time`() {
        val rows =
            repo.queryParticipantAccounts(studyId.stringRepresentation, null, null, null, null, null, null)

        val alice = rows.single { it.participantJson.contains("alice@example.com") }
        assertEquals(aliceLatestGroupId.stringRepresentation, alice.deploymentId)
        assertEquals(Instant.parse("2026-03-01T10:20:30.123456Z"), alice.invitedOn)

        val bob = rows.single { it.participantJson.contains("UsernameAccountIdentity") }
        assertNull(bob.invitedOn)
    }

    @Test
    fun `sorting by invitedOn orders deployed participants and keeps undeployed last`() {
        fun order(direction: SortDirection): List<String> {
            val rows =
                repo.queryParticipantAccounts(
                    studyId.stringRepresentation,
                    null,
                    null,
                    null,
                    null,
                    direction,
                    ParticipantOrderBy.InvitedOn,
                )
            return rows.map { row -> listOf("alice", "bob", "carol").single { row.participantJson.contains(it) } }
        }

        assertEquals(listOf("carol", "alice", "bob"), order(SortDirection.Asc))
        assertEquals(listOf("alice", "carol", "bob"), order(SortDirection.Desc))
    }

    @Test
    fun `a deployed group without a deployment row stays deployed with no invitation time`() {
        jdbc.update("DELETE FROM deployments")

        val rows =
            repo.queryParticipantAccounts(studyId.stringRepresentation, null, null, null, null, null, null)

        val alice = rows.single { it.participantJson.contains("alice@example.com") }
        assertEquals(true, alice.isDeployed)
        assertNull(alice.invitedOn)
    }

    private fun insertDeployment(
        groupId: UUID,
        createdOn: String,
    ) {
        jdbc.update(
            "INSERT INTO deployments (snapshot) VALUES (?::jsonb)",
            """{"id":"${groupId.stringRepresentation}","createdOn":"$createdOn"}""",
        )
    }

    @Test
    fun `legacy participant list returns a json array of every participant`() {
        val json =
            repo.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                studyId.stringRepresentation,
                offset = 0,
                limit = 100,
                search = null,
                isDescending = null,
                sortBy = null,
            )
        assertNotNull(json)
        assertEquals(3, json.split("accountIdentity").size - 1)
    }

    /** alice (email, two deployed groups), bob (username, staged group), carol (email, one deployed group). */
    private fun seedSnapshot(): RecruitmentSnapshot {
        val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val bob = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val carol = Participant(EmailAccountIdentity("carol@example.com"), UUID.randomUUID())
        fun deployedGroup(
            id: UUID,
            participant: Participant,
        ) = StagedParticipantGroup(id, ParticipantGroupRepresentation("Deployed")).apply {
            addParticipants(setOf(AssignedParticipantRoles(participant.id, AssignedTo.All)))
            markAsDeployed()
        }

        val aliceFirst = deployedGroup(aliceFirstGroupId, alice)
        val aliceLatest = deployedGroup(aliceLatestGroupId, alice)
        val carolGroup = deployedGroup(carolGroupId, carol)
        val staged =
            StagedParticipantGroup(UUID.randomUUID(), ParticipantGroupRepresentation("Staged")).apply {
                addParticipants(setOf(AssignedParticipantRoles(bob.id, AssignedTo.Roles(setOf("nurse")))))
            }
        return RecruitmentSnapshot(
            id = UUID.randomUUID(),
            createdOn = Instant.parse("2026-01-01T00:00:00Z"),
            version = 1,
            studyId = studyId,
            studyProtocol = null,
            invitation = null,
            participants = setOf(alice, bob, carol),
            participantGroups = listOf(aliceFirst, aliceLatest, carolGroup, staged).associateBy { it.id },
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
