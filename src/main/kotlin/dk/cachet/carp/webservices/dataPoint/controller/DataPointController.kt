package dk.cachet.carp.webservices.dataPoint.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.constants.RequestParamName
import dk.cachet.carp.webservices.common.query.QueryUtil
import dk.cachet.carp.webservices.dataPoint.controller.DataPointController.Companion.DATA_POINT_BASE
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.service.DataPointService
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Deprecated("Data Point is deprecated, use DataStream instead.")
@RestController
@RequestMapping(value = [DATA_POINT_BASE])
class DataPointController(private val dataPointService: DataPointService) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val DATA_POINT_BASE = "/api/deployments/{${PathVariableName.DEPLOYMENT_ID}}/data-points"
        const val GET_DATAPOINT_BY_ID = "/{${PathVariableName.DATA_POINT_ID}}"
        const val COUNT = "/count"

        /** Others */
        // A page of data points is JSONB-laden, so the page size bounds how much of the heap one request
        // can claim on the JVM that is also ingesting live data. Callers page with `page`.
        const val DEFAULT_PAGE_SIZE = 1000
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

    /**
     * Returns the total number of data points for the given deployment.
     * In the request parameters, a `query` parameter can be used to filter the data.
     * It accepts standard RSQL queries like the `getAll` endpoint. Can also be null.
     */
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
