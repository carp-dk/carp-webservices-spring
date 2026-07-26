package dk.cachet.carp.webservices.study.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceDecorator
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.domain.InactiveDeploymentInfo
import dk.cachet.carp.webservices.study.domain.ParticipantGroupsStatus
import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
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

    suspend fun getParticipantGroupsStatus(studyId: UUID): ParticipantGroupsStatus

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
