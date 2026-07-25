package dk.cachet.carp.webservices.migration

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
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizationStore
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ExitCodeGenerator
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.mock.env.MockEnvironment
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

@Testcontainers(disabledWithoutDocker = true)
class RecruitmentNormalizationRunnerPostgresTest {
    private lateinit var jdbc: JdbcTemplate

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
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        // Tables V7's indexes and V8's FK depend on.
        jdbc.execute(
            """
            CREATE TABLE recruitments (
                id INTEGER PRIMARY KEY,
                snapshot JSONB,
                created_at TIMESTAMP WITHOUT TIME ZONE,
                updated_at TIMESTAMP WITHOUT TIME ZONE
            );
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
        seed()
    }

    @Test
    fun `apply normalizes decodable recruitments, skips undecodable, and is idempotent`() {
        runner("apply").run(mockk<ApplicationArguments>())

        // id 4 undecodable + id 5 NULL are both recorded as SKIPPED.
        assertEquals(mapOf("MIGRATED" to 3L, "SKIPPED" to 2L), outcomeCounts())
        assertEquals(0L, count("SELECT COUNT(*) FROM core_data_migration_failures"))
        assertEquals("COMPLETED", string("SELECT status FROM core_data_migration_runs WHERE mode = 'APPLY'"))
        assertEquals("2", string("SELECT report->>'skippedCount' FROM core_data_migration_runs WHERE mode = 'APPLY'"))

        // 1 + 1 + 3 participants, 1 + 1 + 2 groups, 1 + 1 + 2 members, 2 deployed groups.
        assertEquals(5L, count("SELECT COUNT(*) FROM recruitment_participants"))
        assertEquals(4L, count("SELECT COUNT(*) FROM recruitment_participant_groups"))
        assertEquals(4L, count("SELECT COUNT(*) FROM recruitment_participant_group_members"))
        assertEquals(2L, count("SELECT COUNT(*) FROM recruitment_participant_groups WHERE is_deployed"))

        // Re-apply must not duplicate (idempotent replace).
        runner("apply").run(mockk<ApplicationArguments>())
        assertEquals(5L, count("SELECT COUNT(*) FROM recruitment_participants"))
        assertEquals(4L, count("SELECT COUNT(*) FROM recruitment_participant_groups"))
        assertEquals(4L, count("SELECT COUNT(*) FROM recruitment_participant_group_members"))
    }

    @Test
    fun `verify passes for persisted recruitments after apply`() {
        runner("apply").run(mockk<ApplicationArguments>())
        runner("verify").run(mockk<ApplicationArguments>())

        val verifyRunId = long("SELECT id FROM core_data_migration_runs WHERE mode = 'VERIFY'")
        val outcomes =
            jdbc.queryForList(
                "SELECT outcome, COUNT(*) AS c FROM core_data_migration_rows WHERE run_id = ? " +
                    "GROUP BY outcome ORDER BY outcome",
                verifyRunId,
            ).associate { it["outcome"] as String to (it["c"] as Number).toLong() }

        assertEquals(mapOf("SKIPPED" to 2L, "VALIDATED" to 3L), outcomes)
        assertEquals("COMPLETED", string("SELECT status FROM core_data_migration_runs WHERE mode = 'VERIFY'"))
    }

    @Test
    fun `strip empties migrated blobs, keeps the envelope, skips unmigrated, and is idempotent`() {
        runner("apply").run(mockk<ApplicationArguments>())
        runner("strip").run(mockk<ApplicationArguments>())

        // Decodable recruitments 1-3 had their two maps emptied; the envelope (studyId) is preserved.
        assertEquals(
            listOf(1, 2, 3),
            jdbc.queryForList(
                "SELECT id FROM recruitments WHERE snapshot->'participants' = '[]'::jsonb " +
                    "AND snapshot->'participantGroups' = '{}'::jsonb ORDER BY id",
                Int::class.java,
            ),
        )
        assertEquals(1L, count("SELECT count(*) FROM recruitments WHERE id = 1 AND snapshot->>'studyId' IS NOT NULL"))
        // Undecodable recruitment 4 was NOT stripped (still holds its participants).
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT jsonb_array_length(snapshot->'participants') FROM recruitments WHERE id = 4",
                Int::class.java,
            ),
        )

        val strip1 = long("SELECT id FROM core_data_migration_runs WHERE mode = 'STRIP' ORDER BY id LIMIT 1")
        assertEquals(mapOf("STRIPPED" to 3L, "SKIPPED" to 2L), outcomesForRun(strip1))

        // Re-strip is a no-op: the emptied blobs are UNCHANGED.
        runner("strip").run(mockk<ApplicationArguments>())
        val strip2 = long("SELECT id FROM core_data_migration_runs WHERE mode = 'STRIP' ORDER BY id DESC LIMIT 1")
        assertEquals(mapOf("SKIPPED" to 2L, "UNCHANGED" to 3L), outcomesForRun(strip2))
    }

    private fun outcomesForRun(runId: Long): Map<String, Long> =
        jdbc.queryForList(
            "SELECT outcome, COUNT(*) AS c FROM core_data_migration_rows WHERE run_id = ? GROUP BY outcome",
            runId,
        ).associate { it["outcome"] as String to (it["c"] as Number).toLong() }

    @Test
    fun `schema constraints reject invalid participant and member rows`() {
        jdbc.update("INSERT INTO recruitments (id, snapshot) VALUES (10, '{}'::jsonb)")

        // email type without an email address, and an unknown identity type.
        assertFailsWith<Exception> { insertParticipant("p1", "email", email = null) }
        assertFailsWith<Exception> { insertParticipant("p2", "phone", email = "e@x.com") }

        insertParticipant("p3", "email", email = "e@x.com")
        jdbc.update(
            "INSERT INTO recruitment_participant_groups (recruitment_id, study_id, group_id, is_deployed) " +
                "VALUES (10, 's', 'g', true)",
        )
        // assigned_all=true with role names, and assigned_all=false without role names.
        assertFailsWith<Exception> { insertMember(assignedAll = true, roleNames = "'{a}'") }
        assertFailsWith<Exception> { insertMember(assignedAll = false, roleNames = "NULL") }
        // A member for a participant that does not exist violates the FK.
        assertFailsWith<Exception> {
            jdbc.update(
                "INSERT INTO recruitment_participant_group_members " +
                    "(study_id, group_id, participant_id, assigned_all) VALUES ('s', 'g', 'ghost', true)",
            )
        }
    }

    private fun insertParticipant(
        participantId: String,
        type: String,
        email: String?,
    ) = jdbc.update(
        "INSERT INTO recruitment_participants " +
            "(recruitment_id, study_id, participant_id, account_identity_type, email_address, sort_order) " +
            "VALUES (10, 's', ?, ?, ?, 0)",
        participantId,
        type,
        email,
    )

    private fun insertMember(
        assignedAll: Boolean,
        roleNames: String,
    ) = jdbc.update(
        "INSERT INTO recruitment_participant_group_members " +
            "(study_id, group_id, participant_id, assigned_all, role_names) " +
            "VALUES ('s', 'g', 'p3', $assignedAll, $roleNames)",
    )

    // ---- helpers -------------------------------------------------------------

    private fun runner(mode: String): RecruitmentNormalizationRunner {
        val context = mockk<ConfigurableApplicationContext>(relaxed = true)
        every { context.getBeansOfType(ExitCodeGenerator::class.java) } returns emptyMap()
        val dataSource = checkNotNull(jdbc.dataSource)
        return RecruitmentNormalizationRunner(
            jdbc,
            TransactionTemplate(DataSourceTransactionManager(dataSource)),
            RecruitmentNormalizationStore(jdbc),
            MockEnvironment()
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("carp.recruitment-normalization.mode", mode)
                .withProperty("carp.recruitment-normalization.resume", "false"),
            context,
        )
    }

    private fun seed() {
        val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val bob = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val carol = Participant(EmailAccountIdentity("carol@example.com"), UUID.randomUUID())

        insert(1, snapshot(setOf(alice), listOf(group("A", setOf(role(alice, AssignedTo.All)), deployed = false))))
        insert(
            2,
            snapshot(setOf(bob), listOf(group(null, setOf(role(bob, AssignedTo.Roles(setOf("supervisor")))), true))),
        )
        insert(
            3,
            snapshot(
                setOf(alice, bob, carol),
                listOf(
                    group("g1", setOf(role(alice, AssignedTo.All)), deployed = false),
                    group("g2", setOf(role(bob, AssignedTo.Roles(setOf("nurse")))), deployed = true),
                ),
            ),
        )
        insertRaw(4, undecodableSnapshot()) // stale "type" discriminator -> SKIPPED
        jdbc.update("INSERT INTO recruitments (id, snapshot) VALUES (5, NULL)") // out of scope
    }

    private fun role(
        p: Participant,
        assignedTo: AssignedTo,
    ) = AssignedParticipantRoles(p.id, assignedTo)

    private fun group(
        name: String?,
        roles: Set<AssignedParticipantRoles>,
        deployed: Boolean,
    ) = StagedParticipantGroup(UUID.randomUUID(), ParticipantGroupRepresentation(name)).apply {
        addParticipants(roles)
        if (deployed) markAsDeployed()
    }

    private fun snapshot(
        participants: Set<Participant>,
        groups: List<StagedParticipantGroup>,
    ) = RecruitmentSnapshot(
        id = UUID.randomUUID(),
        createdOn = Instant.parse("2026-01-01T00:00:00Z"),
        version = 1,
        studyId = UUID.randomUUID(),
        studyProtocol = null,
        invitation = null,
        participants = participants,
        participantGroups = groups.associateBy { it.id },
    )

    private fun insert(
        id: Int,
        snapshot: RecruitmentSnapshot,
    ) = insertRaw(id, WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), snapshot))

    private fun insertRaw(
        id: Int,
        json: String,
    ) = jdbc.update("INSERT INTO recruitments (id, snapshot) VALUES (?, CAST(? AS jsonb))", id, json)

    private fun undecodableSnapshot(): String {
        val gid = UUID.randomUUID().stringRepresentation
        val pid = UUID.randomUUID().stringRepresentation
        return """
            {
              "id":"${UUID.randomUUID()}","createdOn":"2024-01-01T00:00:00Z","version":0,
              "studyId":"${UUID.randomUUID()}","studyProtocol":null,"invitation":null,
              "participants":[{"accountIdentity":{"__type":"dk.cachet.carp.common.application.users.UsernameAccountIdentity","username":"x"},"id":"$pid"}],
              "participantGroups":{"$gid":{"id":"$gid","representation":{"name":null},
                "_roleAssignments":[{"assignedRoles":{"type":"dk.cachet.carp.common.application.users.AssignedTo.Roles","roleNames":["Participant"]},"participantId":"$pid"}],
                "isDeployed":true}}
            }
            """.trimIndent()
    }

    private fun outcomeCounts(): Map<String, Long> =
        jdbc.queryForList("SELECT outcome, COUNT(*) AS c FROM core_data_migration_rows GROUP BY outcome")
            .associate { it["outcome"] as String to (it["c"] as Number).toLong() }

    private fun count(sql: String): Long = checkNotNull(jdbc.queryForObject(sql, Long::class.java))

    private fun string(sql: String): String = checkNotNull(jdbc.queryForObject(sql, String::class.java))

    private fun long(sql: String): Long = checkNotNull(jdbc.queryForObject(sql, Long::class.java))

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
