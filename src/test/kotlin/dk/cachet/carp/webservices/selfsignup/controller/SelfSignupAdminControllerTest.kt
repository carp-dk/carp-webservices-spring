package dk.cachet.carp.webservices.selfsignup.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.selfsignup.dto.SelfSignupConfigResponseDto
import dk.cachet.carp.webservices.selfsignup.service.SelfSignupService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

class SelfSignupAdminControllerTest {
    private val selfSignupService: SelfSignupService = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(SelfSignupAdminController(selfSignupService)).build()
    }

    private val studyId = UUID.randomUUID().stringRepresentation
    private val config =
        SelfSignupConfigResponseDto(
            shortCode = "ABCDE",
            enabled = true,
            participantRoleName = "participant",
            maxParticipants = 100,
            currentParticipantCount = 0,
        )

    @Nested
    inner class Enable {
        @Test
        fun `delegates to the service and returns its config`() {
            runTest {
                coEvery { selfSignupService.enable(any(), any()) } returns config

                mockMvc
                    .put("/api/studies/$studyId/self-signup") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"maxParticipants":100,"participantRoleName":"participant",
                                "clientId":"client","redirectUri":"https://example.com"}"""
                    }.andExpect { status { isOk() } }
                coVerify(exactly = 1) { selfSignupService.enable(any(), any()) }
            }
        }

        @Test
        fun `rejects a request missing required fields`() {
            runTest {
                mockMvc
                    .put("/api/studies/$studyId/self-signup") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"maxParticipants":100}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { selfSignupService.enable(any(), any()) }
            }
        }

        @Test
        fun `rejects a non-positive maxParticipants`() {
            runTest {
                mockMvc
                    .put("/api/studies/$studyId/self-signup") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"maxParticipants":0,"participantRoleName":"participant",
                                "clientId":"client","redirectUri":"https://example.com"}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { selfSignupService.enable(any(), any()) }
            }
        }

        @Test
        fun `rejects an expirationSeconds beyond the operational maximum`() {
            runTest {
                // Above Int.MAX_VALUE, which KeycloakFacade.createAnonymousAccountsBulk narrows to an Int
                // via a plain toInt() - this must be rejected here, not silently wrapped downstream.
                mockMvc
                    .put("/api/studies/$studyId/self-signup") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"maxParticipants":100,"participantRoleName":"participant",
                                "clientId":"client","redirectUri":"https://example.com",
                                "expirationSeconds":9999999999}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { selfSignupService.enable(any(), any()) }
            }
        }
    }

    @Nested
    inner class GetConfig {
        @Test
        fun `returns the service's config`() {
            runTest {
                coEvery { selfSignupService.getConfig(any()) } returns config

                mockMvc
                    .get("/api/studies/$studyId/self-signup")
                    .andExpect { status { isOk() } }
                coVerify(exactly = 1) { selfSignupService.getConfig(any()) }
            }
        }
    }

    @Nested
    inner class End {
        @Test
        fun `delegates to the service and returns its config`() {
            runTest {
                coEvery { selfSignupService.end(any()) } returns config.copy(enabled = false)

                mockMvc
                    .delete("/api/studies/$studyId/self-signup")
                    .andExpect { status { isOk() } }
                coVerify(exactly = 1) { selfSignupService.end(any()) }
            }
        }
    }
}
