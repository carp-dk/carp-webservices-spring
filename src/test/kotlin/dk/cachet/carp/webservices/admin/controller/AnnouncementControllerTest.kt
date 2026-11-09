package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.domain.AnnouncementSeverity
import dk.cachet.carp.webservices.admin.service.AnnouncementService
import io.mockk.coEvery
import io.mockk.mockk
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

class AnnouncementControllerTest {
    private val announcementService = mockk<AnnouncementService>()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(AnnouncementController(announcementService)).build()
    }

    private fun getAnnouncement() =
        mockMvc.perform(
            asyncDispatch(
                mockMvc.perform(get("/api/announcement")).andExpect(request().asyncStarted()).andReturn(),
            ),
        )

    @Test
    fun `returns the current announcement without who set it`() {
        coEvery { announcementService.get() } returns
            Announcement(
                "Maintenance tonight.",
                AnnouncementSeverity.INFO,
                Instant.parse("2026-11-09T10:00:00Z"),
                "admin",
            )

        getAnnouncement()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Maintenance tonight."))
            .andExpect(jsonPath("$.severity").value("INFO"))
            .andExpect(jsonPath("$.updatedBy").doesNotExist())
            .andExpect(header().string("Cache-Control", "max-age=60, public"))
    }

    @Test
    fun `returns 204 when no announcement is set`() {
        coEvery { announcementService.get() } returns null

        getAnnouncement().andExpect(status().isNoContent)
    }
}
