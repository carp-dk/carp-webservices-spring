package dk.cachet.carp.webservices.statistics.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.datastream.dto.DateQuantityPairDb
import dk.cachet.carp.webservices.datastream.dto.LocationCoordinatesDb
import dk.cachet.carp.webservices.datastream.repository.DataStreamIdRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.study.dto.ApplicationDataQuantityPairDb
import dk.cachet.carp.webservices.study.repository.StudyRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class StatisticsServiceImplTest {
    private val studyRepository = mockk<StudyRepository>()
    private val accountService = mockk<AccountService>()
    private val applicationDataService = ApplicationDataService(ObjectMapper())
    private val dataStreamIdRepository = mockk<DataStreamIdRepository>()
    private val dataStreamSequenceRepository = mockk<DataStreamSequenceRepository>()
    private val clock = Clock.fixed(Instant.parse("2025-02-21T12:00:00Z"), ZoneOffset.UTC)
    private val locationStreamIds = listOf(1, 2, 3, 4)
    private val locationLookbackFrom = Instant.parse("2024-02-22T12:00:00Z")

    private val sut =
        StatisticsServiceImpl(
            studyRepository,
            accountService,
            applicationDataService,
            dataStreamIdRepository,
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
                mapOf(
                    "2025-02-15" to 0L,
                    "2025-02-16" to 2L,
                    "2025-02-17" to 0L,
                    "2025-02-18" to 5L,
                    "2025-02-19" to 0L,
                    "2025-02-20" to 0L,
                    "2025-02-21" to 7L,
                ),
                result.dailyDatastreamUploads,
            )
            assertEquals(
                mapOf(
                    "Research App" to 10L,
                    "Ops App" to 2L,
                    "not-set" to 10L,
                ),
                result.studiesByApplications,
            )
            assertEquals(
                listOf(
                    LocationCoordinatesDto(55.7814989, 12.5183833),
                    LocationCoordinatesDto(56.162939, 10.203921),
                ),
                result.locationwiseDataUploads,
            )
        }

    @Test
    fun `should return empty locationwise uploads when no location streams exist`() =
        runTest {
            mockEmptyOverviewDependencies()

            val result = sut.getOverview()

            assertEquals(0, result.totalLiveStudies)
            assertEquals(0, result.totalParticipants)
            assertEquals(0, result.totalResearchers)
            assertEquals(
                mapOf(
                    "2025-02-15" to 0L,
                    "2025-02-16" to 0L,
                    "2025-02-17" to 0L,
                    "2025-02-18" to 0L,
                    "2025-02-19" to 0L,
                    "2025-02-20" to 0L,
                    "2025-02-21" to 0L,
                ),
                result.dailyDatastreamUploads,
            )
            assertEquals(emptyMap(), result.studiesByApplications)
            assertEquals(emptyList(), result.locationwiseDataUploads)
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
        every { dataStreamIdRepository.getAllIdsByName("location") } returns locationStreamIds
        every {
            dataStreamSequenceRepository.getLatestLocationCoordinatesByDataStreamIds(
                locationStreamIds,
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

    private fun mockEmptyOverviewDependencies() {
        every { studyRepository.countLiveStudies() } returns 0
        coEvery { accountService.getCountByRole(Role.PARTICIPANT) } returns 0
        coEvery { accountService.getCountByRole(Role.RESEARCHER) } returns 0
        every {
            dataStreamSequenceRepository.getDailyUploadCountsSince(Instant.parse("2025-02-15T00:00:00Z"))
        } returns emptyList()
        every { studyRepository.getLiveStudyCountsByApplicationData() } returns emptyList()
        every { dataStreamIdRepository.getAllIdsByName("location") } returns emptyList()
    }
}
