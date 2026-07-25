package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupRepresentation
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.common.input.WS_JSON
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Integration coverage for [RecruitmentNormalizationStore] against a real database: the write paths
 * ([RecruitmentNormalizationStore.replace] and [RecruitmentNormalizationStore.append]) plus read-back
 * and reconstruction — the data layer behind the flag-on cutover.
 */
@Testcontainers(disabledWithoutDocker = true)
class RecruitmentNormalizationStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: RecruitmentNormalizationStore
    private val studyId = UUID.randomUUID()

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
        store = RecruitmentNormalizationStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        jdbc.execute(
            """
            CREATE TABLE recruitments (id INTEGER PRIMARY KEY, snapshot JSONB);
            CREATE TABLE data_stream_sequence (id INTEGER PRIMARY KEY, data_stream_id INTEGER, last_sequence_id INTEGER);
            CREATE TABLE data_stream_ids (id INTEGER PRIMARY KEY, study_deployment_id VARCHAR(255) NOT NULL);
            """.trimIndent(),
        )
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V7__add_core_1_3_data_migration_tracking.sql"),
            )
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V8__normalize_recruitment_participants_and_groups.sql"),
            )
        }
        jdbc.update("INSERT INTO recruitments (id, snapshot) VALUES (1, '{}'::jsonb)")
    }

    @Test
    fun `replace then read-back reconstructs the snapshot`() {
        val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val bob = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val group = stagedGroup("G", setOf(role(alice, AssignedTo.All), role(bob, AssignedTo.Roles(setOf("x")))), true)
        val snapshot = snapshot(setOf(alice, bob), mapOf(group.id to group))

        store.replace(1, RecruitmentNormalizer.decompose(snapshot))

        assertReconstructs(snapshot)
        // Idempotent replace.
        store.replace(1, RecruitmentNormalizer.decompose(snapshot))
        assertEquals(2L, count("SELECT COUNT(*) FROM recruitment_participants"))
    }

    @Test
    fun `append adds rows without touching existing and continues sort_order`() {
        val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val g1 = stagedGroup("g1", setOf(role(alice, AssignedTo.All)), deployed = false)
        store.replace(1, RecruitmentNormalizer.decompose(snapshot(setOf(alice), mapOf(g1.id to g1))))

        val bob = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val g2 = stagedGroup("g2", setOf(role(bob, AssignedTo.Roles(setOf("nurse")))), deployed = true)
        store.append(1, studyId.stringRepresentation, listOf(bob), mapOf(g2.id to g2))

        val rows = store.readRows(1)
        assertEquals(2, rows.participants.size)
        assertEquals(2, rows.groups.size)
        assertEquals(2, rows.members.size)
        // sort_order continues: alice=0, bob=1.
        assertEquals(listOf(0, 1), rows.participants.sortedBy { it.sortOrder }.map { it.sortOrder })
    }

    @Test
    fun `replace applies only the delta and leaves unchanged rows untouched`() {
        val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val group = stagedGroup("old name", setOf(role(alice, AssignedTo.All)), deployed = false)
        store.replace(1, RecruitmentNormalizer.decompose(snapshot(setOf(alice), mapOf(group.id to group))))

        // Stamp a sentinel created_at; a reinsert (replace-all) would reset it to now().
        jdbc.update(
            "UPDATE recruitment_participants SET created_at = '2000-01-01' WHERE participant_id = ?",
            alice.id.stringRepresentation,
        )

        // Second replace: alice unchanged, add bob, rename + deploy the group.
        val bob = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val renamed =
            StagedParticipantGroup(group.id, ParticipantGroupRepresentation("new name")).apply {
                addParticipants(setOf(role(alice, AssignedTo.All)))
                markAsDeployed()
            }
        store.replace(
            1,
            RecruitmentNormalizer.decompose(snapshot(setOf(alice, bob), mapOf(renamed.id to renamed))),
        )

        // alice's row was not reinserted => diff, not replace-all.
        val aliceCreatedAt =
            jdbc.queryForObject(
                "SELECT created_at::text FROM recruitment_participants WHERE participant_id = ?",
                String::class.java,
                alice.id.stringRepresentation,
            )
        assertEquals(true, aliceCreatedAt!!.startsWith("2000-01-01"))

        val rows = store.readRows(1)
        assertEquals(2, rows.participants.size)
        val updatedGroup = rows.groups.single()
        assertEquals("new name", updatedGroup.name)
        assertEquals(true, updatedGroup.isDeployed)
    }

    // ---- helpers -------------------------------------------------------------

    private fun assertReconstructs(snapshot: RecruitmentSnapshot) {
        val rows = store.readRows(1)
        val reconstructed =
            RecruitmentNormalizer.reconstruct(
                RecruitmentNormalizer.decompose(snapshot)
                    .copy(participants = rows.participants, groups = rows.groups, members = rows.members),
            )
        assertEquals(canonical(encode(snapshot)), canonical(encode(reconstructed)))
    }

    private fun role(
        p: Participant,
        assignedTo: AssignedTo,
    ) = AssignedParticipantRoles(p.id, assignedTo)

    private fun stagedGroup(
        name: String?,
        roles: Set<AssignedParticipantRoles>,
        deployed: Boolean,
    ) = StagedParticipantGroup(UUID.randomUUID(), ParticipantGroupRepresentation(name)).apply {
        addParticipants(roles)
        if (deployed) markAsDeployed()
    }

    private fun snapshot(
        participants: Set<Participant>,
        groups: Map<UUID, StagedParticipantGroup>,
    ) = RecruitmentSnapshot(
        id = UUID.randomUUID(),
        createdOn = Instant.parse("2026-01-01T00:00:00Z"),
        version = 1,
        studyId = studyId,
        studyProtocol = null,
        invitation = null,
        participants = participants,
        participantGroups = groups,
    )

    private fun encode(snapshot: RecruitmentSnapshot) =
        WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), snapshot)

    private fun canonical(json: String) = CanonicalJson.canonicalize(WS_JSON.parseToJsonElement(json))

    private fun count(sql: String): Long = checkNotNull(jdbc.queryForObject(sql, Long::class.java))

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
