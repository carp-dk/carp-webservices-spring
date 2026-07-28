package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Deletes expired anonymous accounts (Phase 2 of the cleanup feature — see docs/ws-exports.md). Reads the
 * `anonymous_account_cleanup` schedule, and for each study past its `delete_after` asks the carp-keycloak
 * extension to delete that study's group members (the now-empty group is deliberately left in place — a
 * concurrent generation may reuse it). The extension keeps accounts that still have an active session or
 * were generated after delete_after; only once all a study's members are deleted is its schedule row dropped.
 *
 * A run deletes at most [maxAccountsPerRun] accounts (a hard cap counted from accounts actually deleted,
 * enforced down to the individual request), so a large study doesn't trigger an unbounded burst of Keycloak
 * deletions — its remainder is swept on later runs. Each study is itself swept over several
 * [perRequestLimit]-sized calls to keep every request short.
 */
@Service
class AnonymousAccountCleanupService(
    private val store: AnonymousAccountCleanupStore,
    private val issuerFacade: IssuerFacade,
    @param:Value("\${cleanup.anonymous-accounts.max-accounts-per-run:1000000}")
    private val maxAccountsPerRun: Long,
    @param:Value("\${cleanup.anonymous-accounts.per-request-limit:10000}")
    private val perRequestLimit: Int,
    @param:Value("\${cleanup.anonymous-accounts.max-studies-per-scan:500}")
    private val maxStudiesPerScan: Int,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    suspend fun cleanupExpired() {
        val expired = store.findExpired(Instant.now(), maxStudiesPerScan)
        if (expired.isEmpty()) return

        LOGGER.info("Anonymous-account cleanup: ${expired.size} expired study group(s) to consider")
        var deletedThisRun = 0L
        for (study in expired) {
            // Check the budget BEFORE starting a study so the run can't overshoot maxAccountsPerRun by a
            // whole study; the per-study call below is itself capped to the remaining budget.
            if (deletedThisRun >= maxAccountsPerRun) break
            try {
                // Stamp the attempt up front so this study rotates to the back of the queue even if it
                // can't finish (round-robin fairness — a stuck study must not starve the others).
                store.markAttempted(study.studyId)
                deletedThisRun += sweepStudyGroup(study.studyId, study.deleteAfter, maxAccountsPerRun - deletedThisRun)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Keep the schedule row and move on; the study is retried on the next run.
                LOGGER.error("Failed to clean up anonymous accounts for study ${study.studyId}", e)
            }
        }
    }

    /**
     * Sweep one study's group with repeated bounded calls, threading the extension's continuation [cursor] so
     * kept members aren't re-scanned. Deletes at most [budget] accounts — a hard cap on the run's total,
     * enforced by shrinking each request's examine-limit to the remaining budget. Returns the number actually
     * deleted. Completes (drops the schedule row) once the whole group is scanned with nothing held back by an
     * active session; keeps the row when active sessions remain or the budget runs out (swept on a later run).
     */
    private suspend fun sweepStudyGroup(
        studyId: String,
        deleteAfter: Instant,
        budget: Long,
    ): Long {
        // Only delete accounts created before the recorded deletion time, so a concurrent generation's fresh
        // accounts (created "now", after delete_after) are never swept.
        val createdBefore = deleteAfter.toEpochMilli()
        var deleted = 0L
        var cursor = 0
        var keptActive = false
        while (deleted < budget) {
            val limit = minOf(perRequestLimit.toLong(), budget - deleted).toInt()
            val result = issuerFacade.deleteAnonymousAccounts(studyId, createdBefore, limit, cursor)
            deleted += result.deleted
            if (result.activeSkipped > 0) keptActive = true
            if (result.exhausted) {
                if (keptActive) {
                    // Old accounts we may not delete yet remain — keep the row and retry on a later run.
                    LOGGER.info("Anonymous account group for study $studyId kept active session(s); will retry")
                } else if (store.deleteIfUnchanged(studyId, deleteAfter) > 0) {
                    // Whole group scanned, nothing held back by a session => done for this schedule.
                    LOGGER.info("Deleted all anonymous accounts for study $studyId")
                } else {
                    // A concurrent generation extended the timer meanwhile — keep its new schedule.
                    LOGGER.info("Schedule for study $studyId changed during cleanup; kept for the new timer")
                }
                return deleted
            }
            cursor = result.cursor
        }
        LOGGER.info("Anonymous cleanup budget reached mid-study $studyId; will resume next run")
        return deleted
    }
}
