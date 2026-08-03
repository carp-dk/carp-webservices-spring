package dk.cachet.carp.webservices.study.scheduler

import dk.cachet.carp.webservices.study.service.impl.AnonymousAccountCleanupService
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Daily sweep that deletes expired anonymous accounts via [AnonymousAccountCleanupService]. Runs on the admin
 * (client-credentials) token inside the service, so it needs no user security context. Mirrors ExportCleanup.
 *
 * DISABLED by default: anonymous accounts are retained indefinitely, so this bean is only registered when
 * `cleanup.anonymous-accounts.enabled=true`. The per-study cleanup ledger is still recorded on every
 * generation regardless (see [AnonymousService.recordCleanupSchedule]), so flipping the flag on re-enables
 * deletion with the schedule already populated — no backfill needed. See docs/ws-exports.md.
 */
@Component
@ConditionalOnProperty(
    name = ["cleanup.anonymous-accounts.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class AnonymousAccountCleanupScheduler(
    private val cleanupService: AnonymousAccountCleanupService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Scheduled(cron = "\${cleanup.anonymous-accounts.cron:0 30 3 * * ?}")
    fun cleanup() {
        LOGGER.info("Cleaning up expired anonymous accounts...")
        runBlocking { cleanupService.cleanupExpired() }
    }
}
