package dk.cachet.carp.webservices.study.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceDecorator
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.domain.InactiveDeploymentInfo
import dk.cachet.carp.webservices.study.domain.ParticipantGroupsStatus
import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.dto.DeploymentStatusCountsDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsRequestDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsResponseDto

interface RecruitmentService {
    val core: RecruitmentServiceDecorator

    suspend fun inviteUserWithRole(
        studyId: UUID,
        email: String,
        role: Role,
    )

    suspend fun removeStudyManager(
        studyId: UUID,
        email: String,
    ): Boolean

    @Suppress("LongParameterList")
    suspend fun getParticipants(
        studyId: UUID,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDescending: Boolean?,
        sortBy: ParticipantOrderBy? = null,
    ): List<Account>

    suspend fun queryParticipantAccounts(
        studyId: UUID,
        request: ParticipantAccountsRequestDto,
    ): ParticipantAccountsResponseDto

    suspend fun getInactiveDeployments(
        studyId: UUID,
        lastUpdate: Int,
        offset: Int = 0,
        limit: Int = -1,
    ): List<InactiveDeploymentInfo>

    /**
     * Paged, filterable participant-group status.
     *
     * When [page] and [size] are both provided, returns one page of matching groups plus the total
     * count; when omitted, returns every group (legacy behavior). [search] matches deployment id and
     * participant account identity; [status] filters by deployment state (e.g. "Running"). Only the
     * returned page is enriched with account/last-upload data, so that work is O(page), not O(all).
     */
    @Suppress("LongParameterList")
    suspend fun getParticipantGroupsStatus(
        studyId: UUID,
        page: Int? = null,
        size: Int? = null,
        search: String? = null,
        status: String? = null,
    ): ParticipantGroupsStatus

    /** Aggregate counts of participant-group deployment statuses for the study overview pie chart. */
    suspend fun getParticipantGroupStatusCounts(studyId: UUID): DeploymentStatusCountsDto

    /**
     * Temporary workaround for a bug in carp.core 1.3.0 [RecruitmentServiceHost.stopParticipantGroup],
     * whose `require(recruitment.id == studyId)` compares the recruitment aggregate id against the
     * study id and therefore always throws. Reimplemented here until the upstream fix ships.
     */
    suspend fun stopParticipantGroup(
        studyId: UUID,
        groupId: UUID,
    ): ParticipantGroupStatus
}
