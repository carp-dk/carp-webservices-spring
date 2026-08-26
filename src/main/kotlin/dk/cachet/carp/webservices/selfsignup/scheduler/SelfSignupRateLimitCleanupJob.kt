package dk.cachet.carp.webservices.selfsignup.scheduler

import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupRateLimitStore
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Sweeps `self_signup_rate_limit` windows older than the retention horizon - nothing else prunes this
 * table, so it grows unbounded without this job. Always on (unlike the anonymous-account cleanup
 * scheduler): this is plain table hygiene, not a feature with retention implications worth gating behind
 * a flag.
 */
@Component
class SelfSignupRateLimitCleanupJob(
    private val store: SelfSignupRateLimitStore,
    @param:Value("\${self-signup.rate-limit.retention-hours:1}")
    private val retentionHours: Long,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Scheduled(cron = "\${self-signup.rate-limit.cleanup-cron:0 0 * * * ?}")
    fun cleanup() {
        val deleted = store.deleteOlderThan(Instant.now().minus(Duration.ofHours(retentionHours)))
        if (deleted > 0) LOGGER.info("Self-signup rate-limit cleanup removed $deleted expired window(s)")
    }
}
