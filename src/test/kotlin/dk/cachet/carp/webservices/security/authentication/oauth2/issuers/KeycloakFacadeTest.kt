package dk.cachet.carp.webservices.security.authentication.oauth2.issuers

import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.KeycloakFacade
import dk.cachet.carp.webservices.security.authorization.Role
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

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
                server.enqueue( // successful retry
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
