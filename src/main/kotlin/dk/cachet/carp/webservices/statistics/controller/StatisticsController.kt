package dk.cachet.carp.webservices.statistics.controller

import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.statistics.dto.StatisticsOverviewDto
import dk.cachet.carp.webservices.statistics.service.StatisticsService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(StatisticsController.STATISTICS_BASE)
class StatisticsController(
    private val statisticsService: StatisticsService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        const val STATISTICS_BASE = "/api/internal/statistics"
        const val OVERVIEW = "/overview"
        const val LOCATION_DATA_UPLOADS = "/locations"
    }

    @GetMapping(OVERVIEW)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAdmin()")
    suspend fun getOverview(): StatisticsOverviewDto {
        LOGGER.info("Start GET: $STATISTICS_BASE$OVERVIEW")
        return statisticsService.getOverview()
    }

    @GetMapping(LOCATION_DATA_UPLOADS)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAdmin()")
    suspend fun getLocationDataUploads(): List<LocationCoordinatesDto> {
        LOGGER.info("Start GET: $STATISTICS_BASE$LOCATION_DATA_UPLOADS")
        return statisticsService.getLocationDataUploads()
    }
}
