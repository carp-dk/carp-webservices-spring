package dk.cachet.carp.webservices.statistics.dto

data class StatisticsOverviewDto(
    val totalLiveStudies: Long,
    val totalParticipants: Long,
    val totalResearchers: Long,
    val dailyDatastreamUploads: Map<String, Long>,
    val studiesByApplications: Map<String, Long>,
    val locationwiseDataUploads: List<LocationCoordinatesDto>,
)
