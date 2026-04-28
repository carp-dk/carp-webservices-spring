package dk.cachet.carp.webservices.statistics.service

import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.statistics.dto.StatisticsOverviewDto

interface StatisticsService {
    suspend fun getOverview(): StatisticsOverviewDto

    suspend fun getLocationDataUploads(): List<LocationCoordinatesDto>
}
