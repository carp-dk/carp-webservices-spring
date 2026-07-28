package dk.cachet.carp.webservices.study.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/** A study whose anonymous accounts are past their deletion time; [accountCount] bounds a cleanup run. */
data class ExpiredAnonymousAccounts(
    val studyId: String,
    val deleteAfter: Instant,
    val accountCount: Long,
)

/**
 * JdbcTemplate-backed access to the `anonymous_account_cleanup` table (one row per study). Follows the
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

    /**
     * Studies whose accounts are past [now], capped at [limit] rows. Ordered by last_attempted_at (nulls
     * first) so a study that can't finish this run rotates to the back and doesn't starve the others;
     * delete_after breaks ties (oldest-expired first).
     */
    fun findExpired(
        now: Instant,
        limit: Int,
    ): List<ExpiredAnonymousAccounts> =
        jdbcTemplate.query(
            "SELECT study_id, delete_after, account_count FROM anonymous_account_cleanup " +
                "WHERE delete_after < ? ORDER BY last_attempted_at ASC NULLS FIRST, delete_after ASC LIMIT ?",
            { rs, _ ->
                ExpiredAnonymousAccounts(
                    rs.getString("study_id"),
                    rs.getTimestamp("delete_after").toInstant(),
                    rs.getLong("account_count"),
                )
            },
            Timestamp.from(now),
            limit,
        )

    /** Stamp [studyId]'s last cleanup attempt so the round-robin ordering rotates it to the back. */
    fun markAttempted(
        studyId: String,
        now: Instant = Instant.now(),
    ) {
        jdbcTemplate.update(
            "UPDATE anonymous_account_cleanup SET last_attempted_at = ?, updated_at = ? WHERE study_id = ?",
            Timestamp.from(now),
            Timestamp.from(now),
            studyId,
        )
    }

    /**
     * Remove [studyId]'s schedule row only if its delete_after still equals [deleteAfter]. Returns the number
     * of rows deleted (0 when a concurrent generation extended the timer meanwhile, leaving the new schedule
     * intact) — guarding against erasing a schedule that freshly generated accounts now depend on.
     */
    fun deleteIfUnchanged(
        studyId: String,
        deleteAfter: Instant,
    ): Int =
        jdbcTemplate.update(
            "DELETE FROM anonymous_account_cleanup WHERE study_id = ? AND delete_after = ?",
            studyId,
            Timestamp.from(deleteAfter),
        )
}
