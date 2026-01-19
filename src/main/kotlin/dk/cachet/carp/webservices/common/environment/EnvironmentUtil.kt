package dk.cachet.carp.webservices.common.environment

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentUtil(
    @Value("\${environment.url}") val url: String,
    @Value("\${environment.portalUrl}") val portalUrl: String,
    @Value("\${environment.keycloak-url}") val keycloakUrl: String,
    @Value("\${environment.keycloak-realm}") val realm: String,
    @Value("\${spring.profiles.active}") private val activeProfile: String,
) {
    val profile: EnvironmentProfile by lazy {
        EnvironmentProfile.getEnvironmentProfile(activeProfile)
    }
}
