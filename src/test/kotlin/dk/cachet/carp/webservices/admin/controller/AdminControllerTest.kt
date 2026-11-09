package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.domain.AnnouncementSeverity
import dk.cachet.carp.webservices.admin.dto.SetAnnouncementRequestDto
import dk.cachet.carp.webservices.admin.service.AdminStudyService
import dk.cachet.carp.webservices.admin.service.AnnouncementService
import dk.cachet.carp.webservices.admin.service.ServerInfoService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminControllerTest {
    private val serverInfoService = mockk<ServerInfoService>()
    private val adminStudyService = mockk<AdminStudyService>()
    private val announcementService = mockk<AnnouncementService>()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AdminController(serverInfoService, adminStudyService, announcementService))
                .build()
    }

    private fun putAnnouncement(body: String) =
        put("/api/admin/announcement").contentType(MediaType.APPLICATION_JSON).content(body)

    /** Runs a suspend handler to completion and returns the dispatched result. */
    private fun performAsync(builder: org.springframework.test.web.servlet.RequestBuilder) =
        mockMvc.perform(asyncDispatch(mockMvc.perform(builder).andExpect(request().asyncStarted()).andReturn()))

    @Test
    fun `setting a message returns 200 with the stored announcement`() {
        coEvery { announcementService.set(any()) } returns
            Announcement(
                "Maintenance tonight.",
                AnnouncementSeverity.WARNING,
                Instant.parse("2026-11-09T10:00:00Z"),
                "admin",
            )

        performAsync(putAnnouncement("""{"message":"Maintenance tonight.","severity":"WARNING"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Maintenance tonight."))
            .andExpect(jsonPath("$.severity").value("WARNING"))
            .andExpect(jsonPath("$.updatedBy").value("admin"))

        val expectedRequest = SetAnnouncementRequestDto("Maintenance tonight.", AnnouncementSeverity.WARNING)
        coVerify { announcementService.set(expectedRequest) }
    }

    @Test
    fun `an empty message clears the announcement with 204`() {
        coEvery { announcementService.set(any()) } returns null

        performAsync(putAnnouncement("""{"message":""}""")).andExpect(status().isNoContent)
    }

    @Test
    fun `a message over the length limit is rejected`() {
        val tooLong = "x".repeat(SetAnnouncementRequestDto.MAX_MESSAGE_LENGTH + 1)

        mockMvc.perform(putAnnouncement("""{"message":"$tooLong"}""")).andExpect(status().isBadRequest)

        coVerify(exactly = 0) { announcementService.set(any()) }
    }

    @Test
    fun `the live filter is passed through to the studies listing`() {
        coEvery { adminStudyService.getStudies(true) } returns emptyList()

        performAsync(get("/api/admin/studies").param("live", "true")).andExpect(status().isOk)

        coVerify(exactly = 1) { adminStudyService.getStudies(true) }
    }

    @Test
    fun `every admin endpoint requires the admin role`() {
        val handlers = AdminController::class.declaredMemberFunctions
        assertEquals(setOf("getServerInfo", "getStudies", "setAnnouncement"), handlers.map { it.name }.toSet())
        handlers.forEach { assertEquals("isAdmin()", it.findAnnotation<PreAuthorize>()?.value, it.name) }
    }
}
