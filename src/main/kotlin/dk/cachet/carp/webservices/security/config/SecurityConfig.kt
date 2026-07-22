package dk.cachet.carp.webservices.security.config

import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionHandler
import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionRoot
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.DefaultSpringAddonsJwtDecoderFactory
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.SpringAddonsJwtDecoderFactory
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * The default security configuration is overridden by c4-soft-spring-addons to be easier to configure.
 * The configuration takes place in the application-properties file and there is no need to write boilerplate.
 *
 * There is a lot of useful information about both this add-on, Spring Security and OAuth in general in its
 * [GitHub repository](https://github.com/ch4mpy/spring-addons/tree/master/spring-addons-starter-oidc)
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun methodSecurityExpressionHandler(
        participantRepository: CoreParticipantRepository,
        authenticationService: AuthenticationService,
    ): MethodSecurityExpressionHandler =
        SpringAddonsMethodSecurityExpressionHandler {
            ProxiesMethodSecurityExpressionRoot(participantRepository, authenticationService)
        }

    /**
     * Overrides the spring-addons default (`@ConditionalOnMissingBean`) JWT decoder factory so the
     * JWKS fetch uses sane timeouts instead of Spring Security 7's 500 ms default, which caused
     * sporadic `RemoteKeySourceException: Read timed out` 500s on JWKS (re)fetch when Keycloak was
     * slow. Timeouts are configurable via `keycloak.jwks.*`.
     */
    @Bean
    fun springAddonsJwtDecoderFactory(
        @Value("\${keycloak.jwks.connect-timeout:5s}") connectTimeout: Duration,
        @Value("\${keycloak.jwks.read-timeout:5s}") readTimeout: Duration,
    ): SpringAddonsJwtDecoderFactory {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeout)
                setReadTimeout(readTimeout)
            }
        return DefaultSpringAddonsJwtDecoderFactory(RestTemplate(requestFactory))
    }

    @PostConstruct
    fun init() = SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)
}

/**
 * Proxies the method security expression root to add custom methods.
 *
 * These methods can be used in SpeL-expressions (e.g., `@PreAuthorize`) in Spring Security annotations.
 */
class ProxiesMethodSecurityExpressionRoot(
    private val participantRepository: CoreParticipantRepository,
    private val auth: AuthenticationService,
) : SpringAddonsMethodSecurityExpressionRoot() {
    fun canManageStudy(studyId: UUID?): Boolean =
        studyId != null && auth.hasClaim(Claim.ManageStudy(studyId)) || isAdmin()

    fun canLimitedManageStudy(studyId: UUID?): Boolean =
        studyId != null && auth.hasClaim(Claim.LimitedManageStudy(studyId)) || isAdmin()

    fun isInDeployment(deploymentId: UUID?): Boolean =
        deploymentId != null && auth.hasClaim(Claim.InDeployment(deploymentId)) || isAdmin()

    fun canManageDeployment(deploymentId: UUID?): Boolean =
        deploymentId != null && auth.hasClaim(Claim.ManageDeployment(deploymentId)) || isAdmin()

    fun isCollectionOwner(collectionId: Int?): Boolean =
        collectionId != null && auth.hasClaim(Claim.CollectionOwner(collectionId)) || isAdmin()

    // It is not easy to assign a claim with a studyId when creating deployments, so we resolve, for
    // each deployment the user participates in, which study it belongs to and check for a match.
    // Study managers are already covered by `canManageStudy`/`canLimitedManageStudy` in the
    // `@PreAuthorize` expressions, so only the participant case needs handling here.
    fun isInDeploymentOfStudy(studyId: UUID): Boolean {
        if (isAdmin()) return true

        return auth.getClaims()
            .filterIsInstance<Claim.InDeployment>()
            .any { participantRepository.getStudyIdByDeploymentId(it.deploymentId) == studyId }
    }

    fun isAdmin(): Boolean = hasRole(Role.SYSTEM_ADMIN.toString())
}
