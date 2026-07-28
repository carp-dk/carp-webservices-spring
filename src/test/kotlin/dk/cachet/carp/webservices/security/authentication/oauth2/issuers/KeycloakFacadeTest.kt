package dk.cachet.carp.webservices.security.authentication.oauth2.issuers

import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.KeycloakFacade
import dk.cachet.carp.webservices.security.authorization.Role
import io.mockk.mockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

// KeycloakFacade is more than likely to change when the official keycloak wrappers are released.
class KeycloakFacadeTest {
    private val realm = "test"
    private val clientId = "test"
    private val clientSecret = "test"
    private val environmentUtil = mockk<EnvironmentUtil>()

    @Test
    fun `deleteAccount is not supported`() =
        runTest {
            assertThrows<UnsupportedOperationException> {
                KeycloakFacade("", realm, clientId, clientSecret, environmentUtil).deleteAccount("test")
            }
        }

    @Test
    fun `authenticate reuses the cached admin token across calls`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                // Two distinct tokens are enqueued; if the cache works only the first is ever fetched.
                server.enqueue(tokenResponse("first-token"))
                server.enqueue(tokenResponse("second-token"))

                val facade =
                    KeycloakFacade(
                        server.url("/").toString().trimEnd('/'),
                        realm,
                        clientId,
                        clientSecret,
                        environmentUtil,
                    )

                val first = facade.authenticate()
                val second = facade.authenticate()

                assertEquals("first-token", first.accessToken)
                assertEquals("first-token", second.accessToken)
                assertEquals(1, server.requestCount)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `admin call refreshes the token and retries once on a 401`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(tokenResponse("stale-token")) // initial token fetch (cached)
                server.enqueue(MockResponse().setResponseCode(401)) // cached token rejected server-side
                server.enqueue(tokenResponse("fresh-token")) // refresh after invalidation
                // successful retry
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("42"),
                )

                val facade =
                    KeycloakFacade(
                        server.url("/").toString().trimEnd('/'),
                        realm,
                        clientId,
                        clientSecret,
                        environmentUtil,
                    )

                val count = facade.getCountForRole(Role.RESEARCHER)

                assertEquals(42L, count)
                assertEquals(4, server.requestCount) // token, 401, token refresh, retry
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `authenticate refetches once the cached token has expired`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                // expires_in 300 - 30s refresh margin => the token is served for 270s.
                server.enqueue(tokenResponse("first-token"))
                server.enqueue(tokenResponse("second-token"))

                val clock = MutableClock(Instant.parse("2026-07-21T00:00:00Z"))
                val facade =
                    KeycloakFacade(
                        server.url("/").toString().trimEnd('/'),
                        realm,
                        clientId,
                        clientSecret,
                        environmentUtil,
                        clock,
                    )

                val initial = facade.authenticate()
                clock.instant = clock.instant.plusSeconds(269) // still within the 270s window
                val stillCached = facade.authenticate()
                clock.instant = clock.instant.plusSeconds(2) // now past expiry (271s total)
                val refreshed = facade.authenticate()

                assertEquals("first-token", initial.accessToken)
                assertEquals("first-token", stillCached.accessToken)
                assertEquals("second-token", refreshed.accessToken)
                assertEquals(2, server.requestCount) // one fetch before expiry, one after
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `admin call fails fast instead of hanging when Keycloak does not respond`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(tokenResponse("token"))
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

                val facade =
                    KeycloakFacade(
                        server.url("/").toString().trimEnd('/'),
                        realm,
                        clientId,
                        clientSecret,
                        environmentUtil,
                        responseTimeout = Duration.ofMillis(200),
                    )

                // Guard the test with a bound well above the 200ms response timeout: if the timeout
                // is wired the call throws quickly; if not, withTimeout trips and we fail explicitly
                // rather than hang.
                val error =
                    assertFails {
                        withTimeout(Duration.ofSeconds(3).toMillis()) {
                            facade.getCountForRole(Role.RESEARCHER)
                        }
                    }
                assertTrue(
                    error !is TimeoutCancellationException,
                    "getCountForRole hung; the response timeout was not applied",
                )
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `deleteAnonymousAccounts sends limit, createdBefore and cursor and parses the result`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(tokenResponse("token"))
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"deleted":7,"skipped":2,"activeSkipped":1,"exhausted":true,"cursor":9}""",
                        ),
                )

                val facade =
                    KeycloakFacade(
                        server.url("/").toString().trimEnd('/'),
                        realm,
                        clientId,
                        clientSecret,
                        environmentUtil,
                    )

                val result = facade.deleteAnonymousAccounts("study-1", createdBefore = 1234L, limit = 500, cursor = 42)

                assertEquals(7, result.deleted)
                assertEquals(2, result.skipped)
                assertEquals(1, result.activeSkipped)
                assertTrue(result.exhausted)
                assertEquals(9, result.cursor)

                server.takeRequest() // admin token fetch
                val request = server.takeRequest()
                assertEquals("DELETE", request.method)
                val path = request.path!!
                assertTrue(path.startsWith("/realms/$realm/bulk-users/anonymous/study-1"), "path was $path")
                assertTrue(path.contains("limit=500"), "path was $path")
                assertTrue(path.contains("createdBefore=1234"), "path was $path")
                assertTrue(path.contains("cursor=42"), "path was $path")
            } finally {
                server.shutdown()
            }
        }

    private fun tokenResponse(accessToken: String) =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"access_token":"$accessToken","expires_in":300,"token_type":"Bearer"}""")

    /** A [Clock] whose current instant can be advanced by the test to drive token expiry. */
    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun instant(): Instant = instant

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    }
}
