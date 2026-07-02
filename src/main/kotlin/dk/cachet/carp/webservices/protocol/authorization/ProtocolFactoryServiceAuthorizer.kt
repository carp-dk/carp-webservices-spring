package dk.cachet.carp.webservices.protocol.authorization

import dk.cachet.carp.protocols.application.ProtocolFactoryService
import dk.cachet.carp.protocols.infrastructure.ProtocolFactoryServiceRequest
import dk.cachet.carp.webservices.common.authorization.ApplicationServiceAuthorizer
import org.springframework.stereotype.Service

@Service
class ProtocolFactoryServiceAuthorizer :
    ApplicationServiceAuthorizer<ProtocolFactoryService, ProtocolFactoryServiceRequest<*>> {
    override suspend fun ProtocolFactoryServiceRequest<*>.authorize() {
        when (this) {
            is ProtocolFactoryServiceRequest.CreateCustomProtocol -> Unit
        }
    }

    override suspend fun ProtocolFactoryServiceRequest<*>.changeClaimsOnSuccess(result: Any?) {
        when (this) {
            is ProtocolFactoryServiceRequest.CreateCustomProtocol -> Unit
        }
    }
}
