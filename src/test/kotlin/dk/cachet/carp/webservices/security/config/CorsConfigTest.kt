package dk.cachet.carp.webservices.security.config

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorsConfigTest {
    private val filter = checkNotNull(CorsConfig().corsFilter().filter)

    private fun send(request: MockHttpServletRequest): MockHttpServletResponse =
        MockHttpServletResponse().also { filter.doFilter(request, it, MockFilterChain()) }

    @Test
    fun `allows a bearer-token preflight from any origin, without credentials`() {
        val preflight =
            MockHttpServletRequest("OPTIONS", "/api/deployment-service").apply {
                addHeader(HttpHeaders.ORIGIN, "https://portal.example.org")
                addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type")
            }

        val response = send(preflight)

        assertEquals(200, response.status)
        assertEquals("https://portal.example.org", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertTrue("authorization" in response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).orEmpty())
        assertNull(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }

    @Test
    fun `does not allow credentials on actual requests`() {
        val request =
            MockHttpServletRequest("GET", "/api/announcement").apply {
                addHeader(HttpHeaders.ORIGIN, "https://evil.example.org")
            }

        val response = send(request)

        assertEquals("https://evil.example.org", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertNull(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
    }
}
