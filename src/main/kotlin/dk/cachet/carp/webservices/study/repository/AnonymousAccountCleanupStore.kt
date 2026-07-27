package dk.cachet.carp.webservices.study.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/**
 * JdbcTemplate-backed writes for the `anonymous_account_cleanup` table (one row per study). Follows the
 * same convention as the other bulk/native persistence in this codebase (e.g. RecruitmentNormalizationStore)
 * rather than JPA, so it can be exercised directly against a real database in a Postgres integration test.
 */
@Component
class AnonymousAccountCleanupStore(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * Insert or extend the deletion schedule for [studyId]. [deleteAfter] is the time the study's accounts
     * become eligible for deletion (latest link expiry + safety buffer, computed by the caller). On an existing
     * row the timer is pushed out with GREATEST — so it always reflects the last-expiring batch, even if a
     * later batch had a shorter validity — and [accountCount] is added to the running total. Timestamps are
     * bound as [Timestamp]s from UTC instants, matching how the rest of the codebase writes its timestamps.
     */
    fun upsert(
        studyId: String,
        deleteAfter: Instant,
        accountCount: Long,
        now: Instant = Instant.now(),
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO anonymous_account_cleanup (study_id, delete_after, account_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (study_id) DO UPDATE SET
                delete_after = GREATEST(anonymous_account_cleanup.delete_after, EXCLUDED.delete_after),
                account_count = anonymous_account_cleanup.account_count + EXCLUDED.account_count,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            studyId,
            Timestamp.from(deleteAfter),
            accountCount,
            Timestamp.from(now),
            Timestamp.from(now),
        )
    }
}
