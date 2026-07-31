package dk.cachet.carp.webservices.statistics.dto

import kotlin.time.Instant

data class StatisticsOverviewDto(
    val totalLiveStudies: Long,
    val totalParticipants: Long,
    val totalResearchers: Long,
    val dailyDataStreamUploads: List<DailyDataStreamUploadDto>,
    val studiesByApplications: List<StudiesByApplicationDto>,
)

data class DailyDataStreamUploadDto(
    val time: Instant,
    val value: Long,
)

data class StudiesByApplicationDto(
    val app: String,
    val value: Long,
)
