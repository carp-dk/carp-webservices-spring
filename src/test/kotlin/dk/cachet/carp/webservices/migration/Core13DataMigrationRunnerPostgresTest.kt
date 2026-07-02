package dk.cachet.carp.webservices.migration

import dk.cachet.carp.common.application.UUID
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
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@Testcontainers(disabledWithoutDocker = true)
class Core13DataMigrationRunnerPostgresTest {
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
        jdbc.execute(
            """
            CREATE TABLE deployments (
                id INTEGER PRIMARY KEY,
                snapshot JSONB NOT NULL,
                updated_at TIMESTAMP WITHOUT TIME ZONE
            );
            CREATE TABLE recruitments (
                id INTEGER PRIMARY KEY,
                snapshot JSONB NOT NULL,
                updated_at TIMESTAMP WITHOUT TIME ZONE
            );
            CREATE TABLE data_stream_sequence (
                id INTEGER PRIMARY KEY,
                data_stream_id INTEGER,
                last_sequence_id INTEGER
            );
            CREATE TABLE data_stream_ids (
                id INTEGER PRIMARY KEY,
                study_deployment_id VARCHAR(255) NOT NULL
            );
            """.trimIndent(),
        )
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V7__add_core_1_3_data_migration_tracking.sql"),
            )
        }
    }

    @Test
    fun `apply migrates and records deployment and recruitment outcomes`() {
        val participantId = UUID.randomUUID().stringRepresentation
        jdbc.update(
            "INSERT INTO deployments (id, snapshot, updated_at) VALUES (1, CAST(? AS jsonb), ?)",
            deploymentSnapshot(),
            java.sql.Timestamp.from(java.time.Instant.parse("2025-01-01T00:00:00Z")),
        )
        jdbc.update(
            "INSERT INTO recruitments (id, snapshot) VALUES (1, CAST(? AS jsonb))",
            recruitmentSnapshot(participantId),
        )

        val context = mockk<ConfigurableApplicationContext>(relaxed = true)
        every { context.getBeansOfType(ExitCodeGenerator::class.java) } returns emptyMap()
        val dataSource = checkNotNull(jdbc.dataSource)
        val runner =
            Core13DataMigrationRunner(
                jdbc,
                TransactionTemplate(DataSourceTransactionManager(dataSource)),
                Core13SnapshotTransformer(ObjectMapper()),
                ObjectMapper(),
                MockEnvironment()
                    .withProperty("spring.main.web-application-type", "none")
                    .withProperty("carp.core-1-3-migration.mode", "apply")
                    .withProperty("carp.core-1-3-migration.resume", "false"),
                context,
            )

        runner.run(mockk<ApplicationArguments>())

        val deployment =
            ObjectMapper().readTree(
                jdbc.queryForObject("SELECT snapshot::text FROM deployments", String::class.java),
            )
        val group =
            ObjectMapper().readTree(jdbc.queryForObject("SELECT snapshot::text FROM recruitments", String::class.java))
                .get("participantGroups").properties().first().value
        assertFalse(deployment.has("isStopped"))
        assertNull(deployment.get("stoppedOn").asString(null))
        assertFalse(group.has("_participantIds"))
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM core_data_migration_rows", Long::class.java))
        assertEquals(
            listOf("MIGRATED", "MIGRATED"),
            jdbc.queryForList("SELECT outcome FROM core_data_migration_rows ORDER BY table_name", String::class.java),
        )
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM core_data_migration_failures", Long::class.java))
    }

    private fun deploymentSnapshot(): String =
        """
        {
          "id":"${UUID.randomUUID()}",
          "createdOn":"2024-01-01T00:00:00Z",
          "version":0,
          "studyProtocolSnapshot":{
            "id":"${UUID.randomUUID()}",
            "createdOn":"2024-01-01T00:00:00Z",
            "version":0,
            "ownerId":"${UUID.randomUUID()}",
            "name":"Protocol"
          },
          "participants":[],
          "deviceRegistrationHistory":{},
          "deployedDevices":[],
          "invalidatedDeployedDevices":[],
          "startedOn":null,
          "isStopped":false
        }
        """.trimIndent()

    private fun recruitmentSnapshot(participantId: String): String {
        val groupId = UUID.randomUUID().stringRepresentation
        return """
            {
              "id":"${UUID.randomUUID()}",
              "createdOn":"2024-01-01T00:00:00Z",
              "version":0,
              "studyId":"${UUID.randomUUID()}",
              "studyProtocol":null,
              "invitation":null,
              "participants":[],
              "participantGroups":{
                "$groupId":{
                  "id":"$groupId",
                  "_participantIds":["$participantId"],
                  "isDeployed":false
                }
              }
            }
            """.trimIndent()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
