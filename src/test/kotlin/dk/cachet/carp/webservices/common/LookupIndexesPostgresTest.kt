package dk.cachet.carp.webservices.common

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import kotlin.test.assertTrue

/**
 * Verifies V15 against the real schema (the full Flyway chain): each lookup, written the way its repository
 * writes it, can be served by the index added for it. An index on a slightly different expression is silently
 * ignored by the planner (e.g. `snapshot->'id'` vs `snapshot->>'id'`), so this checks the plan, not just
 * that the index exists. Sequential scans are disabled so the planner picks an index whenever one is usable,
 * regardless of the (empty) table size.
 */
@Testcontainers(disabledWithoutDocker = true)
class LookupIndexesPostgresTest {
    @ParameterizedTest(name = "{1}")
    @MethodSource("lookups")
    fun `lookup uses its index`(
        query: String,
        expectedIndex: String,
    ) {
        val plan = explain(query)

        assertTrue(expectedIndex in plan, "Expected $expectedIndex in plan for:\n$query\n\n$plan")
    }

    private fun explain(query: String): String =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET enable_seqscan = off")
                statement.executeQuery("EXPLAIN $query").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.joinToString("\n")
                }
            }
        }

    companion object {
        // V1 assigns table ownership to `admin`, so the container user must be called that.
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine").withUsername("admin")

        @BeforeAll
        @JvmStatic
        fun migrate() {
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()
                .migrate()
        }

        private const val ID = "'00000000-0000-0000-0000-000000000001'"

        @JvmStatic
        fun lookups(): List<Arguments> =
            listOf(
                // StudyDeploymentRepository
                Arguments.of("SELECT * FROM deployments WHERE snapshot->>'id' = $ID", "idx_deployments_snapshot_id"),
                Arguments.of("SELECT * FROM deployments WHERE snapshot->>'id' IN ($ID)", "idx_deployments_snapshot_id"),
                // ParticipantGroupRepository
                Arguments.of(
                    "SELECT * FROM participant_groups WHERE snapshot->>'studyDeploymentId' = $ID",
                    "idx_participant_groups_snapshot_study_deployment_id",
                ),
                // RecruitmentRepository
                Arguments.of(
                    "SELECT * FROM recruitments WHERE snapshot->>'studyId' = $ID",
                    "idx_recruitments_snapshot_study_id",
                ),
                // StudyRepository
                Arguments.of("SELECT * FROM studies WHERE snapshot->>'id' = $ID", "idx_studies_snapshot_id"),
                // ProtocolRepository
                Arguments.of(
                    "SELECT * FROM protocols WHERE snapshot->>'id' = $ID ORDER BY created_at DESC",
                    "idx_protocols_snapshot_id",
                ),
                // ConsentDocumentRepository
                Arguments.of(
                    "SELECT * FROM consent_documents WHERE deployment_id = $ID",
                    "idx_consent_documents_deployment_id",
                ),
                // CollectionRepository
                Arguments.of("SELECT * FROM collections WHERE study_id = $ID", "idx_collections_study_id_name"),
                Arguments.of(
                    "SELECT * FROM collections WHERE study_id = $ID AND name = 'c'",
                    "idx_collections_study_id_name",
                ),
                Arguments.of(
                    "SELECT * FROM collections WHERE study_deployment_id = $ID AND name = 'c'",
                    "idx_collections_study_deployment_id_name",
                ),
                Arguments.of("SELECT * FROM collections WHERE document_id = 1", "idx_collections_document_id"),
                // DocumentRepository
                Arguments.of(
                    "SELECT * FROM documents WHERE collection_id = 1 AND name = 'd'",
                    "idx_documents_collection_id_name",
                ),
                Arguments.of(
                    "SELECT * FROM documents WHERE collection_id IN (1, 2)",
                    "idx_documents_collection_id_name",
                ),
                // FileRepository
                Arguments.of("SELECT * FROM files WHERE study_id = $ID", "idx_files_study_id"),
            )
    }
}
