package dk.cachet.carp.webservices.statistics.service.impl

import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.datastream.dto.DateQuantityPairDb
import dk.cachet.carp.webservices.datastream.dto.LocationCoordinatesDb
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.statistics.dto.DailyDataStreamUploadDto
import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.statistics.dto.StudiesByApplicationDto
import dk.cachet.carp.webservices.study.dto.ApplicationDataQuantityPairDb
import dk.cachet.carp.webservices.study.repository.StudyRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.sql.Date
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlinx.datetime.Instant as KInstant

class StatisticsServiceImplTest {
    private val studyRepository = mockk<StudyRepository>()
    private val accountService = mockk<AccountService>()
    private val applicationDataService = ApplicationDataService(ObjectMapper())
    private val dataStreamSequenceRepository = mockk<DataStreamSequenceRepository>()
    private val clock = Clock.fixed(Instant.parse("2025-02-21T12:00:00Z"), ZoneOffset.UTC)
    private val locationLookbackFrom = Instant.parse("2024-02-22T12:00:00Z")

    private val sut =
        StatisticsServiceImpl(
            studyRepository,
            accountService,
            applicationDataService,
            dataStreamSequenceRepository,
            clock,
        )

    @Test
    fun `should return overview with account counts`() =
        runTest {
            mockOverviewDependencies()

            val result = sut.getOverview()

            assertEquals(11, result.totalLiveStudies)
            assertEquals(13, result.totalParticipants)
            assertEquals(17, result.totalResearchers)
            assertEquals(
                listOf(
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-15T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-16T00:00:00Z"), 2L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-17T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-18T00:00:00Z"), 5L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-19T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-20T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-21T00:00:00Z"), 7L),
                ),
                result.dailyDataStreamUploads,
            )
            assertEquals(
                listOf(
                    StudiesByApplicationDto("Research App", 10L),
                    StudiesByApplicationDto("Ops App", 2L),
                    StudiesByApplicationDto("not-set", 10L),
                ),
                result.studiesByApplications,
            )
        }

    @Test
    fun `should return locationwise data uploads`() =
        runTest {
            mockLocationwiseDataUploads()

            val result = sut.getLocationDataUploads()

            assertEquals(
                listOf(
                    LocationCoordinatesDto(55.7814989, 12.5183833),
                    LocationCoordinatesDto(56.162939, 10.203921),
                ),
                result,
            )
        }

    @Test
    fun `should return empty overview`() =
        runTest {
            mockEmptyOverviewDependencies()

            val result = sut.getOverview()

            assertEquals(0, result.totalLiveStudies)
            assertEquals(0, result.totalParticipants)
            assertEquals(0, result.totalResearchers)
            assertEquals(
                listOf(
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-15T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-16T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-17T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-18T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-19T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-20T00:00:00Z"), 0L),
                    DailyDataStreamUploadDto(KInstant.parse("2025-02-21T00:00:00Z"), 0L),
                ),
                result.dailyDataStreamUploads,
            )
            assertEquals(emptyList(), result.studiesByApplications)
        }

    @Test
    fun `should return empty locationwise uploads when no location streams exist`() =
        runTest {
            mockEmptyLocationwiseDataUploads()

            val result = sut.getLocationDataUploads()

            assertEquals(emptyList(), result)
        }

    private fun mockOverviewDependencies() {
        every { studyRepository.countLiveStudies() } returns 11
        coEvery { accountService.getCountByRole(Role.PARTICIPANT) } returns 13
        coEvery { accountService.getCountByRole(Role.RESEARCHER) } returns 17
        every {
            dataStreamSequenceRepository.getDailyUploadCountsSince(Instant.parse("2025-02-15T00:00:00Z"))
        } returns
            listOf(
                DateQuantityPairDb(Date.valueOf("2025-02-16"), 2),
                DateQuantityPairDb(Date.valueOf("2025-02-18"), 5),
                DateQuantityPairDb(Date.valueOf("2025-02-21"), 7),
            )
        every { studyRepository.getLiveStudyCountsByApplicationData() } returns
            listOf(
                ApplicationDataQuantityPairDb("""{"applicationName":"Research App"}""", 3),
                ApplicationDataQuantityPairDb("""{"applicationName":"Ops App"}""", 2),
                ApplicationDataQuantityPairDb("""{}""", 4),
                ApplicationDataQuantityPairDb("not-json", 1),
                ApplicationDataQuantityPairDb(null, 5),
                ApplicationDataQuantityPairDb("""{"applicationName":"Research App"}""", 7),
            )
    }

    private fun mockEmptyOverviewDependencies() {
        every { studyRepository.countLiveStudies() } returns 0
        coEvery { accountService.getCountByRole(Role.PARTICIPANT) } returns 0
        coEvery { accountService.getCountByRole(Role.RESEARCHER) } returns 0
        every {
            dataStreamSequenceRepository.getDailyUploadCountsSince(Instant.parse("2025-02-15T00:00:00Z"))
        } returns emptyList()
        every { studyRepository.getLiveStudyCountsByApplicationData() } returns emptyList()
    }

    private fun mockLocationwiseDataUploads() {
        every {
            dataStreamSequenceRepository.getLatestLocationCoordinatesByDataStreamName(
                "location",
                locationLookbackFrom,
                5000,
            )
        } returns
            listOf(
                LocationCoordinatesDb(55.7814989, 12.5183833),
                LocationCoordinatesDb(55.7814989, 12.5183833),
                LocationCoordinatesDb(56.162939, 10.203921),
                LocationCoordinatesDb(null, 10.203921),
            )
    }

    private fun mockEmptyLocationwiseDataUploads() {
        every {
            dataStreamSequenceRepository.getLatestLocationCoordinatesByDataStreamName(
                "location",
                locationLookbackFrom,
                5000,
            )
        } returns emptyList()
    }
}
