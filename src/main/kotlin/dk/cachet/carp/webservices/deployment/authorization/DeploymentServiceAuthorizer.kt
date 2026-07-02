package dk.cachet.carp.webservices.deployment.authorization

import dk.cachet.carp.deployments.application.DeploymentService
import dk.cachet.carp.deployments.application.StudyDeploymentStatus
import dk.cachet.carp.deployments.infrastructure.DeploymentServiceRequest
import dk.cachet.carp.webservices.common.authorization.ApplicationServiceAuthorizer
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import org.springframework.stereotype.Service

@Service
class DeploymentServiceAuthorizer(
    private val authorizationService: AuthorizationService,
    private val authenticationService: AuthenticationService,
) : ApplicationServiceAuthorizer<DeploymentService, DeploymentServiceRequest<*>> {
    override suspend fun DeploymentServiceRequest<*>.authorize() =
        when (this) {
            // Participants should be able to deploy themselves if needed,
            // we shouldn't restrict study deployment creation to researchers/researcher assistants only
            is DeploymentServiceRequest.CreateStudyDeployment -> Unit
            is DeploymentServiceRequest.RemoveStudyDeployments ->
                authorizationService.require(studyDeploymentIds.map { Claim.ManageDeployment(it) }.toSet()) // TODO: 269
            is DeploymentServiceRequest.GetStudyDeploymentStatus ->
                authorizationService.require(Claim.InDeployment(studyDeploymentId))
            is DeploymentServiceRequest.GetStudyDeploymentStatusList ->
                authorizationService.require(studyDeploymentIds.map { Claim.InDeployment(it) }.toSet())
            is DeploymentServiceRequest.RegisterDevice ->
                authorizationService.require(Claim.InDeployment(studyDeploymentId))
            is DeploymentServiceRequest.UnregisterDevice ->
                authorizationService.require(Claim.InDeployment(studyDeploymentId))
            is DeploymentServiceRequest.GetDeviceDeploymentFor ->
                authorizationService.require(Claim.InDeployment(studyDeploymentId))
            is DeploymentServiceRequest.DeviceDeployed ->
                authorizationService.require(Claim.InDeployment(studyDeploymentId))
            is DeploymentServiceRequest.Stop ->
                authorizationService.require(Claim.InDeployment(studyDeploymentId))
        }

    override suspend fun DeploymentServiceRequest<*>.changeClaimsOnSuccess(result: Any?) =
        when (this) {
            is DeploymentServiceRequest.CreateStudyDeployment -> {
                require(result is StudyDeploymentStatus)
// TODO: 270
                if (authenticationService.getRole() == Role.PARTICIPANT) {
                    authorizationService.grantCurrentAuthentication(
                        setOf(Claim.InDeployment(result.studyDeploymentId)),
                    )
                } else {
                    Unit
                }
            }
            is DeploymentServiceRequest.RemoveStudyDeployments -> {
                studyDeploymentIds.forEach {
                    authorizationService.revokeClaimsFromAllAccounts(
                        setOf(Claim.InDeployment(it)),
                    )
                }
            }
            is DeploymentServiceRequest.GetStudyDeploymentStatus,
            is DeploymentServiceRequest.GetStudyDeploymentStatusList,
            is DeploymentServiceRequest.RegisterDevice,
            is DeploymentServiceRequest.UnregisterDevice,
            is DeploymentServiceRequest.GetDeviceDeploymentFor,
            is DeploymentServiceRequest.DeviceDeployed,
            is DeploymentServiceRequest.Stop,
            -> Unit
        }
}
