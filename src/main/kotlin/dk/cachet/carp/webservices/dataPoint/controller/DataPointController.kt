package dk.cachet.carp.webservices.dataPoint.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.constants.RequestParamName
import dk.cachet.carp.webservices.common.query.QueryUtil
import dk.cachet.carp.webservices.dataPoint.controller.DataPointController.Companion.DATA_POINT_BASE
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.dto.CreateDataPointRequestDto
import dk.cachet.carp.webservices.dataPoint.service.DataPointService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

//todo should we delete?
@Deprecated("Data Point is deprecated, use DataStream instead.")
@RestController
@RequestMapping(value = [DATA_POINT_BASE])
class DataPointController(private val dataPointService: DataPointService) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val DATA_POINT_BASE = "/api/deployments/{${PathVariableName.DEPLOYMENT_ID}}/data-points"
        const val GET_DATAPOINT_BY_ID = "/{${PathVariableName.DATA_POINT_ID}}"
        const val BATCH = "/batch"
        const val COUNT = "/count"

        /** Others */
        const val DEFAULT_PAGE_SIZE = 250000
    }

    @GetMapping
    @PreAuthorize("canManageDeployment(#deploymentId) or isInDeployment(#deploymentId)")
    @ResponseStatus(HttpStatus.OK)
    fun getAll(
        @RequestParam(RequestParamName.PAGE, required = false) page: Int?,
        @RequestParam(RequestParamName.QUERY, required = false) query: String?,
        @RequestParam(RequestParamName.SORT, required = false) sort: String?,
        @PathVariable(PathVariableName.DEPLOYMENT_ID, required = true) deploymentId: UUID,
    ): List<DataPoint> {
        LOGGER.info("Start GET: /api/deployments/$deploymentId/data-points")
        val pageRequest = PageRequest.of(page ?: 0, DEFAULT_PAGE_SIZE, QueryUtil.sort(sort))
        return runBlocking { dataPointService.getAll(deploymentId.stringRepresentation, pageRequest, query) }
    }

    @GetMapping(value = [GET_DATAPOINT_BY_ID])
    @PreAuthorize("canManageDeployment(#deploymentId) or isInDeployment(#deploymentId)")
    @ResponseStatus(HttpStatus.OK)
    fun getOne(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @PathVariable(PathVariableName.DATA_POINT_ID) dataPointId: Int,
    ): DataPoint {
        LOGGER.info("Start GET: /api/deployments/$deploymentId/data-points/$dataPointId")
        return dataPointService.getOne(dataPointId)
    }

    @PostMapping
    @PreAuthorize("isInDeployment(#deploymentId)")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @RequestPart file: MultipartFile?,
        @Valid @RequestBody request: CreateDataPointRequestDto,
    ): DataPoint {
        LOGGER.info("Start POST: /api/deployments/$deploymentId/data-points")
        return dataPointService.create(deploymentId.stringRepresentation, file, request)
    }

    @PostMapping(value = [BATCH])
    @PreAuthorize("isInDeployment(#deploymentId)")
    @ResponseStatus(HttpStatus.OK)
    fun createMany(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @RequestPart file: MultipartFile,
    ) {
        LOGGER.info("Start POST: /api/deployments/$deploymentId/data-points/batch")
        dataPointService.createMany(file, deploymentId.stringRepresentation)
    }

    @DeleteMapping(value = [GET_DATAPOINT_BY_ID])
    @PreAuthorize("canManageDeployment(#deploymentId) or isInDeploymentOfStudy(#deploymentId)")
    @ResponseStatus(HttpStatus.OK)
    fun delete(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @PathVariable(PathVariableName.DATA_POINT_ID) dataPointId: Int,
    ) {
        LOGGER.info("Start DELETE: /api/deployments/$deploymentId/data-points/$dataPointId")
        dataPointService.delete(dataPointId)
    }

    @GetMapping(value = [COUNT])
    @PreAuthorize("canManageDeployment(#deploymentId) or isInDeployment(#deploymentId)")
    @ResponseStatus(HttpStatus.OK)
    fun count(
        @RequestParam(RequestParamName.QUERY, required = false) query: String?,
        @PathVariable(PathVariableName.DEPLOYMENT_ID, required = true) deploymentId: UUID,
    ): Long {
        LOGGER.info("Start GET: /api/deployments/$deploymentId/data-points/$COUNT")
        return dataPointService.getNumberOfDataPoints(deploymentId.stringRepresentation, query)
    }
}
