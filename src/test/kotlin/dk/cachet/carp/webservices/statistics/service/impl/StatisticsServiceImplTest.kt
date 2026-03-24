package dk.cachet.carp.webservices.statistics.service.impl

import dk.cachet.carp.webservices.study.repository.StudyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatisticsServiceImplTest {
    private val studyRepository = mockk<StudyRepository>()

    private val sut =
        StatisticsServiceImpl(
            studyRepository,
        )

    @Test
    fun `should return overview with placeholders`() =
        runTest {
            every { studyRepository.countLiveStudies() } returns 11

            val result = sut.getOverview()

            assertEquals(11, result.totalLiveStudies)
            assertEquals(0, result.totalParticipants)
            assertEquals(0, result.totalResearchers)
            assertTrue(result.dailyDatastreamUploads.isEmpty())
            assertTrue(result.operationsByApplications.isEmpty())
            assertTrue(result.locationwiseDataUploads.isEmpty())
        }
}
