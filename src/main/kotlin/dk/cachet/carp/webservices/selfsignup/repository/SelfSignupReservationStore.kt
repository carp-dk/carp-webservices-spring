package dk.cachet.carp.webservices.selfsignup.repository

import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Durable, TTL-bounded holds on self-signup capacity (`self_signup_reservation`), claimed BEFORE calling
 * Keycloak and finalized after. This is the authoritative capacity check for self-signup - checking
 * capacity only after minting a Keycloak account would let every caller in a burst beyond capacity waste
 * an account before only the allowed ones got seated. See the migration's comment for the full rationale.
 *
 * There is deliberately no "release on failure" method: once the Keycloak call has been attempted, a
 * thrown exception does not prove no account was created (a timeout or lost response can follow a call
 * Keycloak actually completed), so deleting the reservation immediately on any failure would destroy the
 * only durable evidence [SelfSignupReservationCleanupJob] needs to reconcile a real orphan later. An
 * unfinalized reservation is instead left to expire and go through that reconciliation path (record the
 * cleanup schedule, then delete) rather than being deleted outright by the caller. See
 * [SelfSignupPublicServiceImpl][dk.cachet.carp.webservices.selfsignup.service.impl.SelfSignupPublicServiceImpl].
 */
@Component
class SelfSignupReservationStore(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Atomically claim a reservation for [studyId], valid for [ttl], BEFORE any Keycloak call. Locks
     * `study_self_signup`'s row for the duration of this (short, all-database) transaction - the same
     * technique [StudySelfSignupStore] uses, just via an explicit lock since claiming needs multiple
     * statements (lock, count live reservations, insert) to be atomic together - so concurrent claims for
     * the SAME study serialize correctly under a burst rather than racing.
     *
     * Returns the reservation's id AND the row's current configuration (locked, so - unlike a caller's
     * earlier, unlocked read - guaranteed fresh as of this claim) if a slot was available (the study is
     * enabled and confirmed-plus-live-reservations is under the cap), or `null` if the study doesn't exist,
     * is disabled, or is full - the caller should reject with 409 immediately, before ever contacting
     * Keycloak. Callers MUST use the returned config, not any earlier read, for the actual Keycloak/
     * participant-creation call: an admin could reconfigure the role, client, redirect URI, subdomain, or
     * expiration between an earlier unlocked read and this claim, and only the config read here - under the
     * same lock that makes the capacity check race-free - is guaranteed current.
     */
    fun tryClaim(
        studyId: String,
        ttl: Duration,
        now: Instant = Instant.now(),
    ): ClaimedReservation? =
        transactionTemplate.execute {
            val config =
                jdbcTemplate
                    .query(
                        "SELECT * FROM study_self_signup WHERE study_id = ? FOR UPDATE",
                        StudySelfSignupStore.rowMapper,
                        studyId,
                    ).firstOrNull() ?: return@execute null
            if (!config.enabled) return@execute null

            val liveReservations =
                checkNotNull(
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM self_signup_reservation WHERE study_id = ? AND expires_at > ?",
                        Int::class.java,
                        studyId,
                        Timestamp.from(now),
                    ),
                )
            if (config.currentParticipantCount + liveReservations >= config.maxParticipants) return@execute null

            val id = UUID.randomUUID().toString()
            jdbcTemplate.update(
                "INSERT INTO self_signup_reservation (id, study_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                id,
                studyId,
                Timestamp.from(now),
                Timestamp.from(now.plus(ttl)),
            )
            ClaimedReservation(id, config)
        }

    /**
     * Convert a still-live reservation into a confirmed slot: deletes the reservation and increments
     * `study_self_signup.current_participant_count`. Does NOT open its own transaction - the caller MUST
     * run this inside the same transaction that durably persists the resulting participant (see
     * [dk.cachet.carp.webservices.study.service.impl.AnonymousServiceImp.addParticipantsAndGroupsCore]'s
     * `beforeCommit`), so both writes commit or roll back together with that participant record.
     *
     * Returns `false` if [reservationId] no longer exists or has expired - the caller must treat that as a
     * failure (abort), since the hold it relied on is no longer valid.
     *
     * Locks `study_self_signup`'s row FIRST, before touching the reservation at all - the same order
     * [tryClaim] uses, not incidentally: if the DELETE below ran before this lock (e.g. lock only at the
     * UPDATE), a concurrent [tryClaim] landing in that gap could see this reservation as already gone from
     * `self_signup_reservation` (or, right at its expiry instant, excluded by tryClaim's own `expires_at >
     * now` filter) while `current_participant_count` still doesn't reflect it either - counted by neither
     * query, letting that concurrent claim admit one slot beyond capacity. Locking here first closes that
     * window: any concurrent [tryClaim] for the same study now either fully precedes this call or fully
     * blocks behind it.
     *
     * Deliberate trade-off: because this lock is held inside the same (comparatively slow) persistence
     * transaction, it stays held until that transaction commits - so a concurrent [tryClaim] for the SAME
     * study blocks behind whichever signup is currently persisting, not just behind this method's own
     * statements. This is intentional: decoupling the two would let a downstream persistence failure
     * permanently burn a capacity slot with no participant and nothing for
     * [SelfSignupReservationCleanupJob][dk.cachet.carp.webservices.selfsignup.scheduler.SelfSignupReservationCleanupJob]
     * to reconcile it against. A study's signup burst is still served far faster than manual review would
     * be; do not "optimize" this without re-solving that atomicity guarantee first.
     */
    fun finalize(
        reservationId: String,
        studyId: String,
        now: Instant = Instant.now(),
    ): Boolean {
        jdbcTemplate.query(
            "SELECT * FROM study_self_signup WHERE study_id = ? FOR UPDATE",
            StudySelfSignupStore.rowMapper,
            studyId,
        )

        val claimed =
            jdbcTemplate.update(
                "DELETE FROM self_signup_reservation WHERE id = ? AND expires_at > ?",
                reservationId,
                Timestamp.from(now),
            ) == 1
        if (!claimed) return false

        jdbcTemplate.update(
            "UPDATE study_self_signup SET current_participant_count = current_participant_count + 1, " +
                "updated_at = ? WHERE study_id = ?",
            Timestamp.from(now),
            studyId,
        )
        return true
    }

    /**
     * Reservations whose TTL has passed. Read-only, deliberately: unlike a finalized reservation (deleted
     * by [finalize] as proof a participant was durably persisted), an EXPIRED, still-present reservation is
     * durable evidence that a signup attempt for that study was made and never completed - the account
     * creation call may have succeeded in Keycloak with the response, the process, or both lost before
     * anything could record that. The caller MUST record a cleanup schedule for a reservation's study
     * BEFORE calling [delete] on it (see SelfSignupReservationCleanupJob) - deleting first would destroy
     * this evidence before it's safely captured elsewhere, so a crash or a failed ledger write in between
     * would silently lose it.
     */
    fun findExpired(now: Instant = Instant.now()): List<ExpiredReservation> =
        jdbcTemplate.query(
            // <=, complementary with finalize()'s `expires_at > ?`: a reservation is live while
            // expires_at > now, so it's expired the instant expires_at <= now - no gap where a reservation
            // sampled at exactly its own expiry instant is treated as neither live nor expired.
            "SELECT id, study_id FROM self_signup_reservation WHERE expires_at <= ?",
            { rs, _ -> ExpiredReservation(rs.getString("id"), rs.getString("study_id")) },
            Timestamp.from(now),
        )

    /**
     * Delete one expired reservation by id. Only call this AFTER a cleanup schedule has been durably
     * recorded for its study (see [findExpired]'s doc) - this method itself has no way to verify that, so
     * getting the order right is the caller's responsibility.
     */
    fun delete(reservationId: String) {
        jdbcTemplate.update("DELETE FROM self_signup_reservation WHERE id = ?", reservationId)
    }
}

data class ExpiredReservation(val id: String, val studyId: String)

/** A successfully-claimed reservation, paired with the row's configuration as of that same locked read. */
data class ClaimedReservation(val reservationId: String, val config: StudySelfSignupConfig)
