package dk.cachet.carp.webservices.deployment.authorization

import dk.cachet.carp.deployments.application.ParticipationService
import dk.cachet.carp.deployments.infrastructure.ParticipationServiceRequest
import dk.cachet.carp.webservices.common.authorization.ApplicationServiceAuthorizer
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import org.springframework.stereotype.Service

@Service
class ParticipationServiceAuthorizer(
    private val auth: AuthorizationService,
) : ApplicationServiceAuthorizer<ParticipationService, ParticipationServiceRequest<*>> {
    override suspend fun ParticipationServiceRequest<*>.authorize() {
        when (this) {
            is ParticipationServiceRequest.GetActiveParticipationInvitations ->
                auth.requireOwner(accountId)
            is ParticipationServiceRequest.GetParticipantData ->
                auth.require(Claim.InDeployment(studyDeploymentId))
            is ParticipationServiceRequest.GetParticipantDataList ->
                auth.require(studyDeploymentIds.map { Claim.InDeployment(it) }.toSet())
            is ParticipationServiceRequest.SetParticipantData ->
                auth.require(Claim.InDeployment(studyDeploymentId))
        }
    }

    override suspend fun ParticipationServiceRequest<*>.changeClaimsOnSuccess(result: Any?) {
        when (this) {
            is ParticipationServiceRequest.GetActiveParticipationInvitations,
            is ParticipationServiceRequest.GetParticipantData,
            is ParticipationServiceRequest.GetParticipantDataList,
            is ParticipationServiceRequest.SetParticipantData,
            -> Unit
        }
    }
}
