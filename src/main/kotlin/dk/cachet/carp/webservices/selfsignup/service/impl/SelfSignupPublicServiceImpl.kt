package dk.cachet.carp.webservices.selfsignup.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.exception.responses.TooManyRequestsException
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import dk.cachet.carp.webservices.selfsignup.dto.SelfSignupResultDto
import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupRateLimitStore
import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupReservationStore
import dk.cachet.carp.webservices.selfsignup.repository.StudySelfSignupStore
import dk.cachet.carp.webservices.selfsignup.service.SelfSignupPublicService
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore.Companion.CLEANUP_BUFFER
import dk.cachet.carp.webservices.study.service.AnonymousService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Suppress("LongParameterList") // Spring constructor injection
class SelfSignupPublicServiceImpl(
    private val services: CoreServiceContainer,
    private val store: StudySelfSignupStore,
    private val reservationStore: SelfSignupReservationStore,
    private val rateLimitStore: SelfSignupRateLimitStore,
    private val accountService: AccountService,
    private val anonymousService: AnonymousService,
    private val anonymousAccountCleanupStore: AnonymousAccountCleanupStore,
    @param:Value("\${self-signup.rate-limit.per-ip-per-minute:20}")
    private val rateLimitPerMinute: Int,
    @param:Value("\${self-signup.reservation.ttl-seconds:300}")
    private val reservationTtlSeconds: Long,
) : SelfSignupPublicService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /**
         * Bounds the Keycloak call below, since it otherwise inherits KeycloakFacade's bulk-client
         * response timeout (default 2 days, sized for a large NDJSON export batch job) - this call is a
         * single account, made synchronously on a servlet thread (see SelfSignupController's runBlocking),
         * so a slow/unresponsive Keycloak must not be able to pin that thread indefinitely. A timeout here
         * composes safely with the existing orphan handling: the reservation is already left untouched on
         * any failure past this point (see the class doc below), so SelfSignupReservationCleanupJob still
         * reconciles a real orphan even if this specific call times out rather than throwing outright.
         */
        private const val KEYCLOAK_CALL_TIMEOUT_MS = 30_000L
    }

    override suspend fun signUp(
        rawCode: String,
        clientIp: String,
    ): SelfSignupResultDto {
        val windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val requestCount = rateLimitStore.incrementAndGet(clientIp, windowStart)
        if (requestCount > rateLimitPerMinute) {
            throw TooManyRequestsException("Too many self-signup attempts from this address; try again shortly.")
        }

        // Only used to resolve the short code to a study id: tryClaim below re-reads (and locks) this
        // table's CURRENT row, so nothing else about this initial read is used past this point - an admin
        // could reconfigure the role/client/redirect URI/subdomain/expiration between this read and the
        // claim, and only the locked re-read is guaranteed current for the actual account creation.
        val studyId =
            store.findByShortCode(rawCode.trim().uppercase())?.studyId
                // quiet: an unauthenticated caller can trigger this on demand (a typo, or an attacker
                // guessing codes), so notifying per-occurrence would flood the client-errors channel - the
                // same reasoning as the ConflictException/TooManyRequestsException handling below.
                ?: throw ResourceNotFoundException("Unknown self-signup code.", quiet = true)

        // Re-check the study's live status now, not just at enable-time: an admin may enable self-signup
        // and the study may later stop while the config row remains enabled=true. No authenticated
        // principal is on this request, so this uses the same undecorated internal accessor as
        // addSelfSignupParticipant.
        val isLive = services.internalStudyService.getStudyStatus(UUID(studyId)).canDeployToParticipants
        if (!isLive) {
            throw ConflictException("Study $studyId is not live; self-signup is unavailable.", quiet = true)
        }

        // Phase 1 of 2: atomically claim a durable, TTL-bounded hold on capacity BEFORE calling Keycloak,
        // and read the row's configuration under that same lock (see tryClaim's doc for why this - not the
        // earlier findByShortCode read above - is the config createAccountAndParticipant must use). This is
        // the authoritative capacity check - under a burst beyond capacity, everyone past this point is
        // rejected immediately with 409, without ever minting a Keycloak account for them. See
        // SelfSignupReservationStore's class doc for why this can't be a plain counter bump here: an
        // interrupted request must not permanently consume capacity, so the claim expires on its own if
        // it's never finalized.
        val claimed =
            reservationStore.tryClaim(studyId, Duration.ofSeconds(reservationTtlSeconds))
                ?: throw ConflictException("Self-signup is closed or full for this study.", quiet = true)

        // Deliberately no release-on-failure here: createAccountAndParticipant's very first action is the
        // Keycloak call, so by the time ANY exception from it could reach this point, Keycloak has already
        // been attempted - and a thrown exception does not mean the account wasn't created (a timeout or a
        // lost response can follow a call Keycloak actually completed server-side). Deleting the
        // reservation on a merely-possible failure would destroy the only durable evidence
        // SelfSignupReservationCleanupJob has to reconcile a real orphan later. Leaving it alone lets the
        // reservation simply expire and be reconciled (record-then-delete, so nothing is lost even then) -
        // release() is reserved for a failure known to have happened BEFORE any external call was made,
        // which does not occur past this point in the current flow.
        return createAccountAndParticipant(claimed.config, claimed.reservationId)
    }

    /**
     * Re-checks liveness and the configured role against the CURRENT protocol, as close as possible to the
     * Keycloak call: both checks in signUp() (before the capacity reservation) and enable()/reconfigure
     * (see SelfSignupServiceImpl.enable) can go stale - an admin may stop the study, or edit/replace its
     * protocol without disabling self-signup first, dropping the role config still points at. Left
     * unchecked, every subsequent signup would create a real Keycloak account before failing inside
     * AnonymousServiceImp.buildParticipants (protocolSnapshot.throwIfInvalidInvitations), orphaning an
     * account on every single attempt instead of failing fast here. Unlike every other failure past
     * tryClaim, a failure HERE is known to happen before any external call is made, so - unlike the rest of
     * createAccountAndParticipant - it's safe (and worth doing) to release the reservation immediately
     * instead of leaving it to expire.
     */
    private suspend fun ensureStillSignable(
        config: StudySelfSignupConfig,
        reservationId: String,
    ) {
        val studyId = UUID(config.studyId)
        val stillLive = services.internalStudyService.getStudyStatus(studyId).canDeployToParticipants
        if (!stillLive) {
            reservationStore.delete(reservationId)
            throw ConflictException("Study ${config.studyId} is not live; self-signup is unavailable.", quiet = true)
        }

        val protocol = services.internalStudyService.getStudyDetails(studyId).protocolSnapshot
        if (protocol == null || protocol.participantRoles.none { it.role == config.participantRoleName }) {
            reservationStore.delete(reservationId)
            throw ConflictException(
                "Study ${config.studyId}'s protocol no longer defines role ${config.participantRoleName}; " +
                    "self-signup is unavailable.",
                quiet = true,
            )
        }
    }

    /** Phase 2 of 2, plus the Keycloak call itself: creates the account, then finalizes [reservationId]. */
    private suspend fun createAccountAndParticipant(
        config: StudySelfSignupConfig,
        reservationId: String,
    ): SelfSignupResultDto {
        ensureStillSignable(config, reservationId)

        // Tracks whether Keycloak has already created (and, via studyId below, group-joined) an account -
        // set as soon as we have *any* response, not only once its fields pass validation. A response
        // object existing at all means the account was created and joined server-side; a null userId/link
        // is a serialization/parsing issue on our end, not evidence the account doesn't exist (mirrors
        // ExportAnonymousParticipants.fastPipeline, which schedules cleanup for `received`, not `written`,
        // accounts for exactly this reason).
        var accountCreated = false
        try {
            // studyId here joins the account to the study's Keycloak group - required, not optional: the
            // existing anonymous-account cleanup sweep (AnonymousAccountCleanupService) deletes by GROUP
            // membership, not by anything in our own ledger. Passing null would leave the account outside
            // that group, so a later cleanup run would find zero members, report the group "exhausted", and
            // delete the schedule row believing it had cleaned up - permanently losing track of it.
            val response =
                withTimeout(KEYCLOAK_CALL_TIMEOUT_MS) {
                    accountService.generateAnonymousAccountBulk(
                        expirationSeconds = config.expirationSeconds,
                        clientId = config.clientId,
                        redirectUri = config.redirectUri,
                        subdomain = config.subdomain,
                        numberOfAccounts = 1,
                        studyId = config.studyId,
                    ).first()
                }
            accountCreated = true

            val userId = response.userId
            val magicLink = response.link
            checkNotNull(userId) { "Anonymous account creation returned no userId." }
            checkNotNull(magicLink) { "Anonymous account creation returned no magic link." }

            anonymousService.addSelfSignupParticipant(
                UUID(config.studyId),
                config.participantRoleName,
                userId,
                magicLink,
                // Phase 2 of 2: runs INSIDE the same transaction that persists the participant/deployment
                // rows below - converts the reservation into a confirmed slot atomically with that record.
                // A crash or thrown exception anywhere in that transaction rolls both back together, so
                // capacity is only ever durably consumed alongside a real, persisted participant. See
                // AnonymousServiceImp.addParticipantsAndGroupsCore's beforeCommit doc.
                reserveCapacity = {
                    if (!reservationStore.finalize(reservationId, config.studyId)) {
                        throw ConflictException(
                            "Self-signup reservation for study ${config.studyId} expired before " +
                                "it could be completed; please try again.",
                        )
                    }
                    // Record the cleanup schedule ATOMICALLY with finalize()/the participant persistence
                    // above: if this write fails, the whole transaction rolls back (participant NOT
                    // persisted, reservation NOT finalized), so the reservation survives intact for
                    // SelfSignupReservationCleanupJob to reconcile later - instead of a fully successful
                    // signup silently ending up with no cleanup tracking at all, which is what happens if
                    // this write happens outside the transaction and is allowed to fail silently. This
                    // deliberately calls the store directly, not AnonymousService.recordCleanupSchedule,
                    // which swallows write failures (the right choice for its normal callers, wrong here:
                    // this failure must propagate and abort the transaction, not be silently absorbed
                    // while the request still returns success).
                    anonymousAccountCleanupStore.upsert(
                        config.studyId,
                        Instant.now().plusSeconds(config.expirationSeconds).plus(CLEANUP_BUFFER),
                        1,
                    )
                },
            )

            return SelfSignupResultDto(magicLink)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (accountCreated) {
                LOGGER.error("Self-signup for study ${config.studyId} orphaned an anonymous account", e)
                // Best-effort immediate attempt for the paths that never reach (or rolled back) the
                // transactional write above - e.g. a malformed response (checkNotNull failing before
                // addSelfSignupParticipant is even called). The reservation itself is left untouched in
                // every one of these cases (see signUp's doc), so SelfSignupReservationCleanupJob remains
                // the durable backstop regardless of whether this particular attempt succeeds.
                anonymousService.recordCleanupSchedule(
                    UUID(config.studyId),
                    Instant.now().plusSeconds(config.expirationSeconds).plus(CLEANUP_BUFFER),
                    1,
                )
            }
            throw e
        }
    }
}
