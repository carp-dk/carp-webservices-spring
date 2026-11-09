package dk.cachet.carp.webservices.security.config

import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BearerTokenResolverTest {
    private val resolver = SecurityConfig().bearerTokenResolver()

    private fun request(path: String) =
        MockHttpServletRequest("GET", path).apply { addHeader("Authorization", "Bearer some-token") }

    @Test
    fun `the public announcement ignores the bearer token so a stale one cannot 401 it`() {
        assertNull(resolver.resolve(request("/api/announcement")))
    }

    @Test
    fun `every other path still resolves the bearer token`() {
        assertEquals("some-token", resolver.resolve(request("/api/admin/announcement")))
        assertEquals("some-token", resolver.resolve(request("/api/studies")))
    }
}
