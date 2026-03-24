package dk.cachet.carp.webservices.protocol.controller

import dk.cachet.carp.protocols.infrastructure.ProtocolFactoryServiceRequest
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceRequest
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.protocol.dto.ProtocolOverview
import dk.cachet.carp.webservices.protocol.serdes.ProtocolFactoryRequestSerializer
import dk.cachet.carp.webservices.protocol.serdes.ProtocolRequestSerializer
import dk.cachet.carp.webservices.protocol.service.ProtocolService
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import io.swagger.v3.oas.annotations.Operation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
class ProtocolController(
    private val services: CoreServiceContainer,
    private val authenticationService: AuthenticationService,
    private val protocolService: ProtocolService,
) {
    companion object {
        val LOGGER: Logger = LogManager.getLogger()
        val serializer: ProtocolRequestSerializer = ProtocolRequestSerializer()
        val factorySerializer: ProtocolFactoryRequestSerializer = ProtocolFactoryRequestSerializer()

        /** Path variables */
        const val PROTOCOL_SERVICE = "/api/protocol-service"
        const val PROTOCOL_FACTORY_SERVICE = "/api/protocol-factory-service"
        const val GET_PROTOCOL_OVERVIEW = "/api/protocols/{${PathVariableName.PROTOCOL_ID}}/latest"
        const val GET_PROTOCOLS_OVERVIEW = "/api/protocols-overview"
    }

    @GetMapping(value = [GET_PROTOCOL_OVERVIEW])
    @PreAuthorize("hasRole('RESEARCH_ASSISTANT' ) or hasRole('RESEARCHER')")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getSingleProtocolOverview(
        @PathVariable(PathVariableName.PROTOCOL_ID) protocolId: String,
    ): String {
        LOGGER.info("/api/protocols/$protocolId/latest")
        val overview =
            protocolService.getSingleProtocolOverview(protocolId)
                ?: throw ResourceNotFoundException("No protocol found with id $protocolId.")
        return WS_JSON.encodeToString(ProtocolOverview.serializer(), overview)
    }

    @GetMapping(value = [GET_PROTOCOLS_OVERVIEW])
    @PreAuthorize("hasRole('RESEARCH_ASSISTANT') or hasRole('RESEARCHER')")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getProtocolsOverview(): List<ProtocolOverview> {
        LOGGER.info("Start GET: /api/protocols")
        return protocolService.getProtocolsOverview(authenticationService.getId())
    }

    @PostMapping(value = [PROTOCOL_SERVICE])
    @Operation(tags = ["protocol/protocols.json"])
    suspend fun protocols(
        @RequestBody httpMessage: String,
    ): ResponseEntity<Any> {
        val request = WS_JSON.decodeFromString(ProtocolServiceRequest.Serializer, httpMessage)
        LOGGER.info("Start POST: $PROTOCOL_SERVICE -> ${request::class.simpleName}")
        val result = protocolService.invoke(request)
        return serializer.serializeResponse(request, result).let { ResponseEntity.ok(it) }
    }

    @PostMapping(value = [PROTOCOL_FACTORY_SERVICE])
    @Operation(tags = ["protocol/protocolFactory.json"])
    suspend fun protocolFactory(
        @RequestBody httpMessage: String,
    ): ResponseEntity<Any> {
        val request = WS_JSON.decodeFromString(ProtocolFactoryServiceRequest.Serializer, httpMessage)
        LOGGER.info("Start POST: $PROTOCOL_FACTORY_SERVICE -> ${request::class.simpleName}")
        val result = services.protocolFactoryService.invoke(request)
        return factorySerializer.serializeResponse(request, result).let { ResponseEntity.ok(it) }
    }
}
