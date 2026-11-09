package dk.cachet.carp.webservices.admin.repository

import dk.cachet.carp.webservices.admin.domain.AdminStudyRow
import dk.cachet.carp.webservices.common.jdbc.TimedJdbcTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instance-wide study overview for system admins: every study with its owner and recruitment figures. Only
 * the recruitment tables (one `GROUP BY study_id` each) and `files` are aggregated; data uploads are per study,
 * see `StudyDataUploadStore`.
 *
 * Runs with a statement timeout (see [TimedJdbcTemplate]). Timestamps are `timestamp without time zone`
 * holding UTC wall-clock time (as Hibernate writes them), so they are read as UTC [LocalDateTime]s.
 */
@Component
class AdminStudyStore(
    jdbcTemplate: JdbcTemplate,
    statementTimeout: Duration = DEFAULT_STATEMENT_TIMEOUT,
) {
    companion object {
        val DEFAULT_STATEMENT_TIMEOUT: Duration = Duration.ofSeconds(15)

        private val LIST_SQL =
            """
            WITH deployment_stats AS (
                SELECT study_id, COUNT(*) AS deployment_count, MIN(created_at) AS first_deployment_at
                FROM recruitment_participant_groups
                WHERE is_deployed
                GROUP BY study_id
            ),
            participant_stats AS (
                SELECT study_id, COUNT(*) AS participant_count
                FROM recruitment_participants
                GROUP BY study_id
            ),
            file_stats AS (
                SELECT study_id, COUNT(*) AS file_count
                FROM files
                GROUP BY study_id
            )
            SELECT s.snapshot->>'id' AS study_id,
                   s.snapshot->>'name' AS name,
                   s.snapshot->>'description' AS description,
                   s.snapshot->>'ownerId' AS owner_id,
                   COALESCE(s.snapshot->>'isLive' = 'true', FALSE) AS is_live,
                   s.snapshot->'protocolSnapshot'->>'applicationData' AS application_data,
                   s.created_at,
                   s.updated_at,
                   ds.first_deployment_at,
                   COALESCE(ps.participant_count, 0) AS participant_count,
                   COALESCE(ds.deployment_count, 0) AS deployment_count,
                   COALESCE(fs.file_count, 0) AS file_count
            FROM studies s
            LEFT JOIN deployment_stats ds ON ds.study_id = s.snapshot->>'id'
            LEFT JOIN participant_stats ps ON ps.study_id = s.snapshot->>'id'
            LEFT JOIN file_stats fs ON fs.study_id = s.snapshot->>'id'
            """.trimIndent()

        private const val ORDER_BY = "ORDER BY s.created_at DESC NULLS LAST, s.id DESC"

        private fun ResultSet.getUtcInstant(column: String): Instant? =
            getObject(column, LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC)

        private val rowMapper =
            RowMapper { rs, _ ->
                AdminStudyRow(
                    studyId = rs.getString("study_id"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    ownerId = rs.getString("owner_id"),
                    isLive = rs.getBoolean("is_live"),
                    applicationData = rs.getString("application_data"),
                    createdAt = rs.getUtcInstant("created_at"),
                    updatedAt = rs.getUtcInstant("updated_at"),
                    firstDeploymentAt = rs.getUtcInstant("first_deployment_at"),
                    participantCount = rs.getLong("participant_count"),
                    deploymentCount = rs.getLong("deployment_count"),
                    fileCount = rs.getLong("file_count"),
                )
            }
    }

    private val timed = TimedJdbcTemplate(jdbcTemplate, statementTimeout)

    /** All studies, newest first; only live (or only non-live) ones when [live] is given. */
    fun findAll(live: Boolean? = null): List<AdminStudyRow> =
        timed.run("Listing studies") { jdbc ->
            if (live == null) {
                jdbc.query("$LIST_SQL\n$ORDER_BY", rowMapper)
            } else {
                jdbc.query(
                    "$LIST_SQL\nWHERE COALESCE(s.snapshot->>'isLive' = 'true', FALSE) = ?\n$ORDER_BY",
                    rowMapper,
                    live,
                )
            }
        }
}
