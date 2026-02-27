package dk.cachet.carp.webservices.protocol.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceDecorator
import dk.cachet.carp.webservices.protocol.dto.ProtocolOverview

interface ProtocolService {
    val core: ProtocolServiceDecorator

    suspend fun getSingleProtocolOverview(protocolId: String): ProtocolOverview?

    suspend fun getProtocolsOverview(accountId: UUID): List<ProtocolOverview>

    suspend fun resolveVersionTag(snapshot: StudyProtocolSnapshot): String
}
