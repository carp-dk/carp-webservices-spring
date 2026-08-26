package dk.cachet.carp.webservices.selfsignup.scheduler

import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupReservationStore
import dk.cachet.carp.webservices.selfsignup.repository.StudySelfSignupStore
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore.Companion.CLEANUP_BUFFER
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Sweeps expired rows from `self_signup_reservation`, reconciling each one into the existing anonymous-
 * account cleanup ledger - see [SelfSignupReservationStore.findExpired] for why this is a correctness
 * backstop, not mere table hygiene: it's the only remaining safeguard against a Keycloak account created
 * just before a crash, timeout, or lost response prevented SelfSignupPublicServiceImpl from ever recording
 * a cleanup schedule for it itself.
 *
 * For each expired reservation, the cleanup schedule is written FIRST and the reservation is deleted only
 * AFTER that write succeeds - never the other way around. Deleting first would destroy the only evidence
 * this signup attempt existed before it's safely captured elsewhere; if the process then crashed, or the
 * ledger write itself failed, that evidence would be gone for good. This also deliberately calls
 * [AnonymousAccountCleanupStore] directly rather than going through
 * [dk.cachet.carp.webservices.study.service.AnonymousService.recordCleanupSchedule], which catches and
 * swallows write failures (a *correct* choice for its normal caller, where a failed schedule write must
 * never fail an otherwise-successful signup) - here the opposite is needed: a failed write MUST prevent
 * the delete below, so the reservation survives to be retried on the next sweep instead of being silently
 * lost. A crash between the two writes is still safe: the schedule is already durably recorded, so the
 * worst case on retry is a harmless duplicate (cumulative) write, never lost evidence.
 */
@Component
class SelfSignupReservationCleanupJob(
    private val store: SelfSignupReservationStore,
    private val configStore: StudySelfSignupStore,
    private val anonymousAccountCleanupStore: AnonymousAccountCleanupStore,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val DEFAULT_EXPIRATION_SECONDS = 86_400L
    }

    @Scheduled(cron = "\${self-signup.reservation.cleanup-cron:0 */5 * * * ?}")
    fun cleanup() {
        val expired = store.findExpired()
        var reconciled = 0
        for (reservation in expired) {
            try {
                val expirationSeconds =
                    configStore.findByStudyId(reservation.studyId)?.expirationSeconds ?: DEFAULT_EXPIRATION_SECONDS
                anonymousAccountCleanupStore.upsert(
                    reservation.studyId,
                    Instant.now().plusSeconds(expirationSeconds).plus(CLEANUP_BUFFER),
                    1,
                )
                store.delete(reservation.id)
                reconciled++
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // Left in place on purpose (see class doc) - it's still expired, so the next sweep retries
                // reconciling it rather than this failure silently losing the evidence.
                LOGGER.error(
                    "Failed to reconcile expired self-signup reservation ${reservation.id} " +
                        "for study ${reservation.studyId}; leaving it for the next sweep",
                    e,
                )
            }
        }
        if (reconciled > 0) {
            LOGGER.info("Self-signup reservation cleanup reconciled $reconciled expired reservation(s)")
        }
    }
}
