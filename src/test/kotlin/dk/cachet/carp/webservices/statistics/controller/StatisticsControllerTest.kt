package dk.cachet.carp.webservices.statistics.controller

import dk.cachet.carp.webservices.common.serialisers.ObjectMapperConfig
import dk.cachet.carp.webservices.statistics.dto.DailyDataStreamUploadDto
import dk.cachet.carp.webservices.statistics.dto.LocationCoordinatesDto
import dk.cachet.carp.webservices.statistics.dto.StatisticsOverviewDto
import dk.cachet.carp.webservices.statistics.dto.StudiesByApplicationDto
import dk.cachet.carp.webservices.statistics.service.StatisticsService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.fasterxml.jackson.databind.ObjectMapper as Jackson2ObjectMapper

class StatisticsControllerTest {
    private val statisticsService: StatisticsService = mockk()
    private val instantModule = SimpleModule().addSerializer(
        Instant::class.java,
        ObjectMapperConfig.KInstantSerializer.INSTANCE
    )
    private val objectMapper = JsonMapper.builder().addModule(instantModule).build()

    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(StatisticsController(statisticsService))
                .setMessageConverters(MappingJackson2HttpMessageConverter(Jackson2ObjectMapper()))
                .build()
    }

    @Test
    fun `should relay task to statistics service`() =
        runTest {
            coEvery { statisticsService.getOverview() } returns overviewDto()

            mockMvc.perform(
                get("/api/internal/statistics/overview")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
        }

    @Test
    fun `should serialize overview statistics rows for browser`() {
        val serialized = objectMapper.writeValueAsString(overviewDto())

        assertEquals(
            """
            {
              "totalLiveStudies" : 1,
              "totalParticipants" : 2,
              "totalResearchers" : 3,
              "dailyDataStreamUploads" : [ {
                "time" : "2026-04-21T00:00:00Z",
                "value" : 0
              } ],
              "studiesByApplications" : [ {
                "app" : "not-set",
                "value" : 40
              } ]
            }
            """.trimIndent(),
            objectMapper.readTree(serialized).toPrettyString(),
        )
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

    private fun overviewDto() =
        StatisticsOverviewDto(
            totalLiveStudies = 1,
            totalParticipants = 2,
            totalResearchers = 3,
            dailyDataStreamUploads =
                listOf(
                    DailyDataStreamUploadDto(
                        time = Instant.parse("2026-04-21T00:00:00Z"),
                        value = 0,
                    ),
                ),
            studiesByApplications =
                listOf(
                    StudiesByApplicationDto(
                        app = "not-set",
                        value = 40,
                    ),
                ),
        )
}
