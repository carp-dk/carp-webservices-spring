package dk.cachet.carp.webservices.common.environment

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentUtil(
    @param:Value("\${environment.url}") val url: String,
    @param:Value("\${environment.portalUrl}") val portalUrl: String,
    @param:Value("\${environment.keycloak-url}") val keycloakUrl: String,
    @param:Value("\${environment.keycloak-realm}") val realm: String,
    @param:Value("\${spring.profiles.active}") private val activeProfile: String,
) {
    val profile: EnvironmentProfile by lazy {
        EnvironmentProfile.getEnvironmentProfile(activeProfile)
    }
}
