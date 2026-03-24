package dk.cachet.carp.webservices.protocol.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceDecorator
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceRequest
import dk.cachet.carp.webservices.protocol.dto.ProtocolOverview

interface ProtocolService {
    val core: ProtocolServiceDecorator

    suspend fun invoke(request: ProtocolServiceRequest<*>): Any?

    suspend fun getSingleProtocolOverview(protocolId: String): ProtocolOverview?

    suspend fun getProtocolsOverview(accountId: UUID): List<ProtocolOverview>
}
