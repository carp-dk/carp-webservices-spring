package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.common.jdbc.TimedJdbcTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Data uploaded to one study's deployed participant groups, as read by [StudyDataUploadStore]. */
data class StudyDataUploadRow(
    val dataStreamCount: Long,
    val measurementCount: Long,
    val lastDataUploadAt: Instant?,
)

/**
 * Totals of the data uploaded to a study, across the data streams of its deployed participant groups.
 *
 * `data_stream_sequence` is by far the largest table and is never aggregated as a whole: per data stream
 * only its highest-sequence row is read (a LATERAL `LIMIT 1` served by the `(data_stream_id,
 * last_sequence_id DESC)` index from V7), so the cost is one index probe per data stream of the study. From
 * that row:
 * - `measurement_count` sums `last_sequence_id + 1` per stream. Sequence ids start at 0 and clients upload
 *   them contiguously, so this is the number of measurements; it is approximate only if a client ever left
 *   gaps.
 * - `last_data_upload_at` is that row's `updated_at`, the same notion of "last upload" as
 *   `RecruitmentRepositoryImpl.findInactiveDeployments`.
 *
 * Runs with a statement timeout (see [TimedJdbcTemplate]). Timestamps are `timestamp without time zone`
 * holding UTC wall-clock time, so they are read as UTC [LocalDateTime]s.
 */
@Component
class StudyDataUploadStore(
    jdbcTemplate: JdbcTemplate,
    statementTimeout: Duration = DEFAULT_STATEMENT_TIMEOUT,
) {
    companion object {
        val DEFAULT_STATEMENT_TIMEOUT: Duration = Duration.ofSeconds(15)

        // `last_sequence_id IS NOT NULL` matters: under DESC, Postgres sorts NULLs first.
        private val SQL =
            """
            SELECT COUNT(*) AS data_stream_count,
                   COALESCE(SUM(latest.last_sequence_id + 1), 0) AS measurement_count,
                   MAX(latest.updated_at) AS last_data_upload_at
            FROM recruitment_participant_groups g
            JOIN data_stream_ids dsi ON dsi.study_deployment_id = g.group_id
            LEFT JOIN LATERAL (
                SELECT dss.last_sequence_id, dss.updated_at
                FROM data_stream_sequence dss
                WHERE dss.data_stream_id = dsi.id AND dss.last_sequence_id IS NOT NULL
                ORDER BY dss.last_sequence_id DESC
                LIMIT 1
            ) latest ON TRUE
            WHERE g.study_id = ? AND g.is_deployed
            """.trimIndent()
    }

    private val timed = TimedJdbcTemplate(jdbcTemplate, statementTimeout)

    fun studyExists(studyId: String): Boolean =
        timed.run("Looking up study $studyId") { jdbc ->
            jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM studies WHERE snapshot->>'id' = ?)",
                Boolean::class.java,
                studyId,
            ) == true
        }

    fun findDataUploads(studyId: String): StudyDataUploadRow =
        timed.run("Computing data uploads for study $studyId") { jdbc ->
            requireNotNull(
                jdbc.queryForObject(SQL, { rs, _ ->
                    StudyDataUploadRow(
                        dataStreamCount = rs.getLong("data_stream_count"),
                        measurementCount = rs.getLong("measurement_count"),
                        lastDataUploadAt =
                            rs.getObject("last_data_upload_at", LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC),
                    )
                }, studyId),
            )
        }
}
