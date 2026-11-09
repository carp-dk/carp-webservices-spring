package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.service.ServerInfoService
import io.mockk.mockk
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminControllerTest {
    private val serverInfoService = mockk<ServerInfoService>()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AdminController(serverInfoService))
                .build()
    }

    @Test
    fun `every admin endpoint requires the admin role`() {
        val handlers = AdminController::class.declaredMemberFunctions
        assertEquals(setOf("getServerInfo"), handlers.map { it.name }.toSet())
        handlers.forEach { assertEquals("isAdmin()", it.findAnnotation<PreAuthorize>()?.value, it.name) }
    }
}
