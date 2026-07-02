package dk.cachet.carp.webservices.protocol.authorization

import dk.cachet.carp.protocols.application.ProtocolService
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceRequest
import dk.cachet.carp.webservices.common.authorization.ApplicationServiceAuthorizer
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import org.springframework.stereotype.Service

@Service
class ProtocolServiceAuthorizer(
    private val auth: AuthorizationService,
) : ApplicationServiceAuthorizer<ProtocolService, ProtocolServiceRequest<*>> {
    override suspend fun ProtocolServiceRequest<*>.authorize() =
        when (this) {
            is ProtocolServiceRequest.Add -> {
                auth.require(Role.RESEARCHER)
                auth.requireOwner(protocol.ownerId)
            }
            is ProtocolServiceRequest.AddVersion -> auth.requireOwner(protocol.ownerId)
            is ProtocolServiceRequest.UpdateParticipantDataConfiguration ->
                auth.require(Claim.ProtocolOwner(protocolId))

            // NOTE: The required authorities for these requests deviate from CORE's recommendations (ProtocolOwner).
            // Protocols don't (shouldn't) contain sensitive data, so we don't need to restrict access to the owner.
            is ProtocolServiceRequest.GetBy,
            is ProtocolServiceRequest.GetAllForOwner,
            is ProtocolServiceRequest.GetVersionHistoryFor,
            -> auth.requireAnyRole(setOf(Role.RESEARCHER, Role.RESEARCH_ASSISTANT))
        }

    override suspend fun ProtocolServiceRequest<*>.changeClaimsOnSuccess(result: Any?) =
        when (this) {
            is ProtocolServiceRequest.Add ->
                auth.grantCurrentAuthentication(Claim.ProtocolOwner(protocol.id))
            is ProtocolServiceRequest.AddVersion,
            is ProtocolServiceRequest.UpdateParticipantDataConfiguration,
            is ProtocolServiceRequest.GetBy,
            is ProtocolServiceRequest.GetAllForOwner,
            is ProtocolServiceRequest.GetVersionHistoryFor,
            -> Unit
        }
}
