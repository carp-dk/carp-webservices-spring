package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupRepresentation
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.common.input.WS_JSON

/**
 * Pure, DB-free decomposition/reconstruction between a core [RecruitmentSnapshot] and its normalized
 * row form ([NormalizedRecruitment]).
 *
 * This is the fidelity-critical core of the participant-group normalization
 * (see docs/participant-group-normalization.md). It is verified by `RecruitmentNormalizerTest` to be
 * serialization-lossless — `reconstruct(decompose(x))` re-serializes to the same JSON as `x` under
 * [WS_JSON] — before any schema/DB work is built on top of it.
 */
object RecruitmentNormalizer {
    private const val IDENTITY_EMAIL = "email"
    private const val IDENTITY_USERNAME = "username"

    /** Split a snapshot into the envelope (both maps emptied) plus typed participant/group/member rows. */
    fun decompose(snapshot: RecruitmentSnapshot): NormalizedRecruitment {
        val envelope = snapshot.copy(participants = emptySet(), participantGroups = emptyMap())
        return NormalizedRecruitment(
            envelopeSnapshot = WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), envelope),
            studyId = snapshot.studyId.stringRepresentation,
            participants = participantRows(snapshot.participants),
            groups = groupRows(snapshot.participantGroups),
            members = memberRows(snapshot.participantGroups),
        )
    }

    /** Participant rows for [participants], numbering `sort_order` from [startSortOrder] (for appends). */
    fun participantRows(
        participants: Collection<Participant>,
        startSortOrder: Int = 0,
    ): List<RecruitmentParticipantRow> =
        participants.mapIndexed { index, participant -> participant.toRow(startSortOrder + index) }

    /** Group rows (without their role assignments) for [groups]. */
    fun groupRows(groups: Map<UUID, StagedParticipantGroup>): List<RecruitmentGroupRow> =
        groups.map { (groupId, group) ->
            RecruitmentGroupRow(groupId.stringRepresentation, group.isDeployed, group.representation.name)
        }

    /** Member (role-assignment) rows across all [groups]. */
    fun memberRows(groups: Map<UUID, StagedParticipantGroup>): List<RecruitmentGroupMemberRow> =
        groups.flatMap { (groupId, group) -> group.roleAssignments.map { it.toRow(groupId.stringRepresentation) } }

    /** Rebuild the exact core snapshot from the envelope and the typed rows. */
    fun reconstruct(normalized: NormalizedRecruitment): RecruitmentSnapshot {
        val envelope = WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), normalized.envelopeSnapshot)

        val participants =
            normalized.participants.sortedBy { it.sortOrder }.map { it.toParticipant() }.toSet()

        val membersByGroup = normalized.members.groupBy { it.groupId }
        val participantGroups =
            normalized.groups.associate { groupRow ->
                val groupId = UUID(groupRow.groupId)
                val group = StagedParticipantGroup(groupId, ParticipantGroupRepresentation(groupRow.name))
                val roleAssignments =
                    membersByGroup[groupRow.groupId].orEmpty().map { it.toAssignedParticipantRoles() }.toSet()
                if (roleAssignments.isNotEmpty()) group.addParticipants(roleAssignments)
                if (groupRow.isDeployed) group.markAsDeployed()
                groupId to group
            }

        return envelope.copy(participants = participants, participantGroups = participantGroups)
    }

    private fun Participant.toRow(sortOrder: Int): RecruitmentParticipantRow =
        when (val identity = accountIdentity) {
            is EmailAccountIdentity ->
                RecruitmentParticipantRow(
                    participantId = id.stringRepresentation,
                    accountIdentityType = IDENTITY_EMAIL,
                    username = null,
                    emailAddress = identity.emailAddress.address,
                    sortOrder = sortOrder,
                )
            is UsernameAccountIdentity ->
                RecruitmentParticipantRow(
                    participantId = id.stringRepresentation,
                    accountIdentityType = IDENTITY_USERNAME,
                    username = identity.username.name,
                    emailAddress = null,
                    sortOrder = sortOrder,
                )
            else -> error("Unsupported account identity type: ${identity::class.simpleName}")
        }

    private fun RecruitmentParticipantRow.toParticipant(): Participant =
        Participant(toAccountIdentity(), UUID(participantId))

    private fun RecruitmentParticipantRow.toAccountIdentity(): AccountIdentity =
        when (accountIdentityType) {
            IDENTITY_EMAIL ->
                EmailAccountIdentity(requireNotNull(emailAddress) { "email identity row without an emailAddress" })
            IDENTITY_USERNAME ->
                UsernameAccountIdentity(requireNotNull(username) { "username identity row without a username" })
            else -> error("Unsupported account identity type: $accountIdentityType")
        }

    private fun AssignedParticipantRoles.toRow(groupId: String): RecruitmentGroupMemberRow =
        when (val roles = assignedRoles) {
            is AssignedTo.All ->
                RecruitmentGroupMemberRow(
                    groupId = groupId,
                    participantId = participantId.stringRepresentation,
                    assignedAll = true,
                    roleNames = null,
                )
            is AssignedTo.Roles ->
                RecruitmentGroupMemberRow(
                    groupId = groupId,
                    participantId = participantId.stringRepresentation,
                    assignedAll = false,
                    roleNames = roles.roleNames.toList(),
                )
        }

    private fun RecruitmentGroupMemberRow.toAssignedParticipantRoles(): AssignedParticipantRoles {
        val assignedTo =
            if (assignedAll) {
                AssignedTo.All
            } else {
                AssignedTo.Roles(requireNotNull(roleNames) { "role-assignment row without roleNames" }.toSet())
            }
        return AssignedParticipantRoles(UUID(participantId), assignedTo)
    }
}
