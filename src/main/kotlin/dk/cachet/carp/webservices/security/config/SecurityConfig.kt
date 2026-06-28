package dk.cachet.carp.webservices.security.config

import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionHandler
import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionRoot
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.context.SecurityContextHolder

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
        studyId != null &&
            auth.getClaims(setOf(Claim.ManageStudy::class))
                .contains(Claim.ManageStudy(studyId)) || isAdmin()

    fun canLimitedManageStudy(studyId: UUID?): Boolean =
        studyId != null &&
            auth.getClaims(setOf(Claim.LimitedManageStudy::class))
                .contains(Claim.LimitedManageStudy(studyId)) || isAdmin()

    fun isInDeployment(deploymentId: UUID?): Boolean =
        deploymentId != null &&
            auth.getClaims(setOf(Claim.InDeployment::class))
                .contains(Claim.InDeployment(deploymentId)) || isAdmin()

    fun canManageDeployment(deploymentId: UUID?): Boolean =
        deploymentId != null &&
            auth.getClaims(setOf(Claim.ManageDeployment::class))
                .contains(Claim.ManageDeployment(deploymentId)) || isAdmin()

    fun isCollectionOwner(collectionId: Int?): Boolean =
        collectionId != null &&
            auth.getClaims(setOf(Claim.CollectionOwner::class))
                .contains(Claim.CollectionOwner(collectionId)) || isAdmin()

    // HACK: it is not easy to assign a claim with a studyId when creating deployments,
    // so we inject `CoreParticipantRepository` here to check whether the user is in a deployment
    // which is part of this study.
    //
    // A potential workaround would be to redesign the endpoints for consent, documents, collections and files.
    fun isInDeploymentOfStudy(studyId: UUID): Boolean =
        runBlocking(Dispatchers.IO + SecurityCoroutineContext()) {
            if (isAdmin()) return@runBlocking true

            val id =
                participantRepository.getRecruitment(studyId)?.participantGroups?.keys
                    ?.firstOrNull {
                        auth.getClaims(setOf(Claim.InDeployment::class)).contains(Claim.InDeployment(it))
                    }

            id != null
        }

    fun isAdmin(): Boolean = hasRole(Role.SYSTEM_ADMIN.toString())
}
