package dk.cachet.carp.webservices.selfsignup.controller

import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.exception.responses.TooManyRequestsException
import dk.cachet.carp.webservices.selfsignup.dto.SelfSignupResultDto
import dk.cachet.carp.webservices.selfsignup.service.SelfSignupPublicService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest

/**
 * Standalone MockMvc coverage of request/response wiring only - this codebase has no existing pattern for
 * exercising the real Spring Security filter chain in a controller test (all controller tests here use
 * `standaloneSetup`, which never invokes it), so the `permit-all` entry for this endpoint and the admin
 * `@PreAuthorize` gate are NOT covered here; verify those manually (see the plan's verification section).
 */
class SelfSignupControllerTest {
    private val selfSignupPublicService: SelfSignupPublicService = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(SelfSignupController(selfSignupPublicService)).build()
    }

    @Test
    fun `returns the magic link on success`() {
        runTest {
            coEvery { selfSignupPublicService.signUp(any(), any()) } returns
                SelfSignupResultDto("https://example.com/magic-link")

            mockMvc
                .post("/api/self-signup/ABCDE")
                .andExpect {
                    status { isOk() }
                    content { json("""{"magicLink":"https://example.com/magic-link"}""") }
                }
            coVerify(exactly = 1) { selfSignupPublicService.signUp("ABCDE", any()) }
        }
    }

    @Test
    fun `maps an unknown code to 404`() {
        runTest {
            coEvery { selfSignupPublicService.signUp(any(), any()) } throws
                ResourceNotFoundException("Unknown self-signup code.")

            mockMvc.post("/api/self-signup/ZZZZZ").andExpect { status { isNotFound() } }
        }
    }

    @Test
    fun `maps a full or disabled study to 409`() {
        runTest {
            coEvery { selfSignupPublicService.signUp(any(), any()) } throws
                ConflictException("Self-signup is closed or full for this study.")

            mockMvc.post("/api/self-signup/ABCDE").andExpect { status { isConflict() } }
        }
    }

    @Test
    fun `maps rate limiting to 429`() {
        runTest {
            coEvery { selfSignupPublicService.signUp(any(), any()) } throws
                TooManyRequestsException("Too many self-signup attempts from this address; try again shortly.")

            mockMvc.post("/api/self-signup/ABCDE").andExpect { status { isEqualTo(429) } }
        }
    }
}
