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
}
