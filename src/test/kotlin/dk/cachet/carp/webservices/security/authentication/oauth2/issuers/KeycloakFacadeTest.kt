package dk.cachet.carp.webservices.security.authentication.oauth2.issuers

import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.KeycloakFacade
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

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
}
