package dk.cachet.carp.webservices.statistics.controller

import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.statistics.dto.StatisticsOverviewDto
import dk.cachet.carp.webservices.statistics.service.StatisticsService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

class StatisticsControllerTest {
    private val statisticsService: StatisticsService = mockk()

    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(StatisticsController(statisticsService)).build()
    }

    @Test
    fun `should relay task to statistics service`() =
        runTest {
            coEvery { statisticsService.getOverview() } returns
                StatisticsOverviewDto(
                    totalLiveStudies = 1,
                    totalParticipants = 2,
                    totalResearchers = 3,
                    dailyDatastreamUploads = emptyMap(),
                    studiesByApplications = emptyMap(),
                )

            mockMvc.perform(
                get("/api/internal/statistics/overview")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
        }

    @Test
    fun `should relay locationwise data uploads task to statistics service`() =
        runTest {
            coEvery { statisticsService.getLocationDataUploads() } returns
                listOf(LocationCoordinatesDto(55.7814989, 12.5183833))

            mockMvc.perform(
                get("/api/internal/statistics/locations")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
        }
}
