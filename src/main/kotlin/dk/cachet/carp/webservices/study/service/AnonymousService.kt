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
