package dk.cachet.carp.webservices.study.scheduler

import dk.cachet.carp.webservices.study.service.impl.AnonymousAccountCleanupService
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Daily sweep that deletes expired anonymous accounts via [AnonymousAccountCleanupService]. Runs on the admin
 * (client-credentials) token inside the service, so it needs no user security context. Mirrors ExportCleanup.
 */
@Component
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
