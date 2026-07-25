package dk.cachet.carp.webservices.study.domain.normalization

/**
 * Row-shaped, fully-typed decomposition of the two unbounded maps inside a
 * [dk.cachet.carp.studies.domain.users.RecruitmentSnapshot] (`participants` and `participantGroups`),
 * mirroring the planned V8 tables `recruitment_participants`, `recruitment_participant_groups`
 * and `recruitment_participant_group_members`.
 *
 * The recruitment envelope (id / version / createdOn / studyId / studyProtocol / invitation) is kept
 * verbatim as [envelopeSnapshot] — the core snapshot with both maps emptied — so nothing in the
 * envelope, including the complex study protocol, is ever normalized.
 *
 * See docs/participant-group-normalization.md.
 */
data class NormalizedRecruitment(
    /** Core `RecruitmentSnapshot` serialized with `participants=[]` and `participantGroups={}`. */
    val envelopeSnapshot: String,
    val studyId: String,
    val participants: List<RecruitmentParticipantRow>,
    val groups: List<RecruitmentGroupRow>,
    val members: List<RecruitmentGroupMemberRow>,
)

/** One row of `recruitment_participants`; captures a core `Participant` losslessly. */
data class RecruitmentParticipantRow(
    val participantId: String,
    /** `"email"` or `"username"` — the concrete `AccountIdentity` subtype. */
    val accountIdentityType: String,
    val username: String?,
    val emailAddress: String?,
    /** Deterministic pagination order only; `participants` is an unordered set in core. */
    val sortOrder: Int,
)

/** One row of `recruitment_participant_groups`; the group envelope minus its role assignments. */
data class RecruitmentGroupRow(
    /** Core group id — equals the study-deployment id once [isDeployed]. */
    val groupId: String,
    val isDeployed: Boolean,
    val name: String?,
)

/** One row of `recruitment_participant_group_members`; a single `AssignedParticipantRoles`. */
data class RecruitmentGroupMemberRow(
    val groupId: String,
    val participantId: String,
    /** `AssignedTo.All` when true; otherwise `AssignedTo.Roles` with [roleNames]. */
    val assignedAll: Boolean,
    val roleNames: List<String>?,
)

/** The three normalized row sets for one recruitment, as read back from the tables. */
data class RecruitmentRows(
    val participants: List<RecruitmentParticipantRow>,
    val groups: List<RecruitmentGroupRow>,
    val members: List<RecruitmentGroupMemberRow>,
)
