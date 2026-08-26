package dk.cachet.carp.webservices.study.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import java.time.Instant

interface AnonymousService {
    suspend fun bulkAddParticipantsAndGroups(
        studyId: UUID,
        roleName: String,
        pair: List<Pair<String, String>>,
    ): List<StagedParticipantGroup>

    /**
     * Build the deployment/participant/group for a single self-signup account (id == [accountId], per the
     * usual anonymous-participant id convention) and persist it in one transaction. Unlike
     * [bulkAddParticipantsAndGroups], this fetches the study via the undecorated internal study service,
     * since the caller (the public self-signup endpoint) has no authenticated principal to satisfy the
     * normal decorated call.
     *
     * [reserveCapacity] runs FIRST, inside the same transaction as the persistence below - it must throw
     * to abort if capacity is unavailable. Running the capacity check here, rather than as an earlier,
     * separately-committed step, is what makes it crash-safe: a process failure or thrown exception
     * anywhere before this transaction commits leaves nothing half-applied, including whatever
     * [reserveCapacity] did - so a study can never appear permanently "full" because of an interrupted
     * request that produced no actual participant.
     */
    suspend fun addSelfSignupParticipant(
        studyId: UUID,
        roleName: String,
        accountId: String,
        magicLink: String,
        reserveCapacity: () -> Unit,
    ): StagedParticipantGroup

    /**
     * Record (or extend) the deletion schedule for [studyId]'s anonymous accounts: [accountCount] more
     * accounts were generated that become eligible for deletion at [deleteAfter] (their link expiry plus a
     * safety buffer). Best-effort — failures are logged, not thrown, so they never fail an otherwise-
     * successful generation.
     */
    suspend fun recordCleanupSchedule(
        studyId: UUID,
        deleteAfter: Instant,
        accountCount: Long,
    )
}
