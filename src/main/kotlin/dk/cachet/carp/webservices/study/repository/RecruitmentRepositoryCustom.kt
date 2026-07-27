package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection

interface RecruitmentRepositoryCustom {
    @Suppress("LongParameterList")
    fun findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDescending: Boolean?,
        sortBy: ParticipantOrderBy?,
    ): String?

    @Suppress("LongParameterList")
    fun queryParticipantAccounts(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDeployed: Boolean?,
        sortDirection: SortDirection?,
        sortBy: ParticipantOrderBy?,
    ): List<ParticipantAccountQueryRow>

    fun countQueryParticipantAccounts(
        studyId: String,
        search: String?,
        isDeployed: Boolean?,
    ): Int

    /**
     * Deployed groups in [studyId] whose latest data upload is strictly before [threshold], ordered
     * oldest-upload-first with `group_id` as a stable tiebreaker (so equal timestamps page consistently).
     * Computed as a single aggregate over the normalized recruitment tables joined
     * to the data-stream tables — groups that never uploaded produce no row (the inner join drops them),
     * matching the legacy "skip null last-upload" behaviour. Reads the normalized tables directly, so it
     * requires the recruitment normalized store to be populated (see docs/participant-group-normalization.md).
     */
    fun findInactiveDeployments(
        studyId: String,
        threshold: java.time.Instant,
        offset: Int?,
        limit: Int?,
    ): List<InactiveDeploymentRow>
}
