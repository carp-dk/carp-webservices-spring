package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.service.AdminStudyService
import dk.cachet.carp.webservices.admin.service.ServerInfoService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminControllerTest {
    private val serverInfoService = mockk<ServerInfoService>()
    private val adminStudyService = mockk<AdminStudyService>()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AdminController(serverInfoService, adminStudyService))
                .build()
    }

    /** Runs a suspend handler to completion and returns the dispatched result. */
    private fun performAsync(builder: org.springframework.test.web.servlet.RequestBuilder) =
        mockMvc.perform(asyncDispatch(mockMvc.perform(builder).andExpect(request().asyncStarted()).andReturn()))

    @Test
    fun `the live filter is passed through to the studies listing`() {
        coEvery { adminStudyService.getStudies(true) } returns emptyList()

        performAsync(get("/api/admin/studies").param("live", "true")).andExpect(status().isOk)

        coVerify(exactly = 1) { adminStudyService.getStudies(true) }
    }

    @Test
    fun `every admin endpoint requires the admin role`() {
        val handlers = AdminController::class.declaredMemberFunctions
        assertEquals(setOf("getServerInfo", "getStudies"), handlers.map { it.name }.toSet())
        handlers.forEach { assertEquals("isAdmin()", it.findAnnotation<PreAuthorize>()?.value, it.name) }
    }
}
