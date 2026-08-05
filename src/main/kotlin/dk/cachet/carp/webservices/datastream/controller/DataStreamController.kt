package dk.cachet.carp.webservices.datastream.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.data.application.DataStreamBatch
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.data.infrastructure.DataStreamServiceRequest
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.dto.DataStreamsSummaryDto
import dk.cachet.carp.webservices.datastream.service.DataStreamService
import dk.cachet.carp.webservices.datastream.service.impl.decompressGzip
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import io.swagger.v3.oas.annotations.Operation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import kotlin.time.Instant

@RestController
class DataStreamController(
    private val dataStreamService: DataStreamService,
    private val authorizationService: AuthorizationService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private val serializer = DataStreamRequestSerializer()

        /** Endpoint URI constants */
        const val DATA_STREAM_SERVICE = "/api/data-stream-service"
        const val DATA_STREAM_SERVICE_GZIP = "/api/data-stream-service-zip"
        const val DATA_STREAMS_SUMMARY = "/api/data-stream-service/summary"
        const val DATA_STREAM_QUERY_BY_TIME = "/api/data-stream-service/query-by-time"
    }

    @GetMapping(value = [DATA_STREAMS_SUMMARY])
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @Suppress("LongParameterList")
    suspend fun getDataStreamsSummary(
        @RequestParam("studyId", required = true) studyId: UUID,
        @RequestParam("deploymentId", required = false) deploymentId: UUID,
        @RequestParam("participantId", required = false) participantId: UUID,
        @RequestParam("scope", required = true) scope: String,
        @RequestParam("type", required = true) type: String,
        @RequestParam("from", required = true) from: Instant,
        @RequestParam("to", required = true) to: Instant,
    ): DataStreamsSummaryDto {
        LOGGER.info(
            "Start GET: /api/data-streams/summary" +
                "?studyId=$studyId&deploymentId=$deploymentId&" +
                "participantId=$participantId&scope=$scope&type=$type&from=$from&to=$to",
        )

        return dataStreamService.getDataStreamsSummary(studyId, deploymentId, participantId, scope, type, from, to)
    }

    @PostMapping(value = [DATA_STREAM_QUERY_BY_TIME])
    @ResponseStatus(HttpStatus.OK)
    @Operation(tags = ["dataStream/queryDataStreamByTime.json"])
    suspend fun queryDataStreamByTime(
        @RequestBody dataStreamId: String,
        @RequestParam("from", required = true) from: Instant,
        @RequestParam("to", required = true) to: Instant,
    ): ResponseEntity<Any> {
        // Body and response are both core (carp.core) types, so mobile clients reuse the serializers
        // they already have: DataStreamId in, DataStreamBatch out. `from`/`to` are plain query params.
        val dataStream = WS_JSON.decodeFromString(DataStreamId.serializer(), dataStreamId)
        LOGGER.info("Start POST: $DATA_STREAM_QUERY_BY_TIME -> dataStream=$dataStream&from=$from&to=$to")
        // Mirror core DataStreamServiceRequest.GetDataStream authorization: require the caller to be in
        // the study deployment. `require` grants system admins implicitly.
        authorizationService.require(Claim.InDeployment(dataStream.studyDeploymentId))
        val batch = dataStreamService.getDataStreamByUpdatedAt(dataStream, from, to)
        return ResponseEntity.ok(WS_JSON.encodeToString(DataStreamBatch.serializer(), batch))
    }

    @PostMapping(value = [DATA_STREAM_SERVICE])
    @Operation(tags = ["dataStream/invoke.json"])
    suspend fun invoke(
        @RequestBody httpMessage: String,
    ): ResponseEntity<Any> {
        val request = WS_JSON.decodeFromString(DataStreamServiceRequest.Serializer, httpMessage)
        LOGGER.info("Start POST: $DATA_STREAM_SERVICE -> ${request::class.simpleName}")
        val ret = dataStreamService.core.invoke(request)
        return serializer.serializeResponse(request, ret).let { ResponseEntity.ok(it) }
    }

    @Operation(tags = ["dataStream/handleCompressedData.json"])
    @PostMapping(value = [DATA_STREAM_SERVICE_GZIP])
    suspend fun handleCompressedData(
        @RequestBody data: ByteArray,
    ): ResponseEntity<Any> {
        LOGGER.info("Start POST: $DATA_STREAM_SERVICE_GZIP")
        val decompressedData = decompressGzip(data)
        val request = WS_JSON.decodeFromString(DataStreamServiceRequest.Serializer, decompressedData)
        return dataStreamService.core.invoke(request).let { ResponseEntity.ok(it) }
    }
}
