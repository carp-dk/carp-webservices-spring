package dk.cachet.carp.webservices.statistics.service.impl

import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.statistics.dto.DailyDataStreamUploadDto
import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.statistics.dto.StatisticsOverviewDto
import dk.cachet.carp.webservices.statistics.dto.StudiesByApplicationDto
import dk.cachet.carp.webservices.statistics.service.StatisticsService
import dk.cachet.carp.webservices.study.repository.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinInstant
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class StatisticsServiceImpl(
    private val studyRepository: StudyRepository,
    private val accountService: AccountService,
    private val applicationDataService: ApplicationDataService,
    private val dataStreamSequenceRepository: DataStreamSequenceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : StatisticsService {
    override suspend fun getOverview(): StatisticsOverviewDto =
        withContext(Dispatchers.IO) {
            val dailyUploadCounts = getDailyUploadCounts()
            val studiesByApplications = getStudiesByApplications()
            StatisticsOverviewDto(
                totalLiveStudies = studyRepository.countLiveStudies(),
                totalParticipants = accountService.getCountByRole(Role.PARTICIPANT),
                totalResearchers = accountService.getCountByRole(Role.RESEARCHER),
                dailyDataStreamUploads = dailyUploadCounts,
                studiesByApplications = studiesByApplications,
            )
        }

    private fun getDailyUploadCounts(): List<DailyDataStreamUploadDto> {
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val startDate = today.minusDays(DAILY_UPLOAD_WINDOW_DAYS - 1)
        val dbCounts =
            dataStreamSequenceRepository.getDailyUploadCountsSince(startDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                .associate { it.date.toLocalDate().toString() to it.quantity }

        return (0L until DAILY_UPLOAD_WINDOW_DAYS).map { offset ->
            val date = startDate.plusDays(offset)
            DailyDataStreamUploadDto(
                time = date.atStartOfDay().toInstant(ZoneOffset.UTC).toKotlinInstant(),
                value = dbCounts[date.toString()] ?: 0L,
            )
        }
    }

    private fun getStudiesByApplications(): List<StudiesByApplicationDto> =
        studyRepository.getLiveStudyCountsByApplicationData()
            .groupBy(
                keySelector = {
                    applicationDataService.extractApplicationName(it.applicationData) ?: APPLICATION_NAME_NOT_SET
                },
                valueTransform = { it.quantity },
            ).map { (applicationName, quantities) ->
                StudiesByApplicationDto(
                    app = applicationName,
                    value = quantities.sum(),
                )
            }

    override suspend fun getLocationDataUploads(): List<LocationCoordinatesDto> =
        withContext(Dispatchers.IO) {
            val from = clock.instant().minus(LOCATION_LOOKBACK_DAYS, ChronoUnit.DAYS)
            val rawCoordinates =
                dataStreamSequenceRepository.getLatestLocationCoordinatesByDataStreamName(
                    LOCATION_STREAM_NAME,
                    from,
                    MAX_LOCATION_RESULTS,
                )

            val mappedCoordinates =
                rawCoordinates
                    .mapNotNull { coordinates ->
                        val latitude = coordinates.latitude
                        val longitude = coordinates.longitude
                        if (latitude == null || longitude == null) {
                            null
                        } else {
                            LocationCoordinatesDto(latitude, longitude)
                        }
                    }
            val distinctCoordinates = mappedCoordinates.distinct()

            distinctCoordinates
        }

    companion object {
        private const val APPLICATION_NAME_NOT_SET = "not-set"
        private const val DAILY_UPLOAD_WINDOW_DAYS = 7L
        private const val LOCATION_STREAM_NAME = "location"
        private const val LOCATION_LOOKBACK_DAYS = 365L
        private const val MAX_LOCATION_RESULTS = 5000
    }
}
