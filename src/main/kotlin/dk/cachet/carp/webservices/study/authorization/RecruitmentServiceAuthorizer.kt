package dk.cachet.carp.webservices.study.authorization

import dk.cachet.carp.studies.application.RecruitmentService
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceRequest
import dk.cachet.carp.webservices.common.authorization.ApplicationServiceAuthorizer
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import org.springframework.stereotype.Service

@Service
class RecruitmentServiceAuthorizer(
    private val auth: AuthorizationService,
    private val participantRepository: CoreParticipantRepository,
) : ApplicationServiceAuthorizer<RecruitmentService, RecruitmentServiceRequest<*>> {
    override suspend fun RecruitmentServiceRequest<*>.authorize() =
        when (this) {
            is RecruitmentServiceRequest.AddParticipantByEmailAddress ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.AddParticipantByUsername ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.GetParticipant ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.GetParticipants ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.InviteNewParticipantGroup ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.CreateParticipantGroup ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.UpdateParticipantGroup -> requireManageGroup(groupId)
            is RecruitmentServiceRequest.InviteParticipantGroup -> requireManageGroup(groupId)
            is RecruitmentServiceRequest.GetParticipantGroupStatusList ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
            is RecruitmentServiceRequest.StopParticipantGroup ->
                auth.requireAnyClaim(
                    setOf(Claim.ManageStudy(studyId), Claim.LimitedManageStudy(studyId)),
                )
        }

    override suspend fun RecruitmentServiceRequest<*>.changeClaimsOnSuccess(result: Any?) =
        when (this) {
            is RecruitmentServiceRequest.AddParticipantByEmailAddress,
            is RecruitmentServiceRequest.AddParticipantByUsername,
            is RecruitmentServiceRequest.GetParticipant,
            is RecruitmentServiceRequest.GetParticipants,
            is RecruitmentServiceRequest.InviteNewParticipantGroup,
            is RecruitmentServiceRequest.CreateParticipantGroup,
            is RecruitmentServiceRequest.UpdateParticipantGroup,
            is RecruitmentServiceRequest.InviteParticipantGroup,
            is RecruitmentServiceRequest.GetParticipantGroupStatusList,
            is RecruitmentServiceRequest.StopParticipantGroup,
            -> Unit
        }

    private suspend fun requireManageGroup(groupId: dk.cachet.carp.common.application.UUID) {
        val recruitment =
            requireNotNull(participantRepository.getRecruitmentWithParticipantGroup(groupId)) {
                "Participant group with ID $groupId does not exist."
            }
        auth.requireAnyClaim(
            setOf(Claim.ManageStudy(recruitment.studyId), Claim.LimitedManageStudy(recruitment.studyId)),
        )
    }
}
