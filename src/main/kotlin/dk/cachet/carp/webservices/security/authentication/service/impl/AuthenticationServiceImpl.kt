package dk.cachet.carp.webservices.security.authentication.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import kotlinx.coroutines.runBlocking
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import kotlin.collections.mapNotNull
import kotlin.reflect.KClass

@Service
class AuthenticationServiceImpl(
    private val participantRepository: CoreParticipantRepository,
) : AuthenticationService {
    override fun getId(): UUID = UUID(getJwtAuthenticationToken().token.subject!!)

    override fun getRole(): Role {
        val role = getJwtAuthenticationToken().authorities.map { Role.fromString(it.authority!!) }.maxOfOrNull { it }

        check(role != null && role != Role.UNKNOWN) { "No role found for the current authentication." }

        return role
    }

    override fun getClaims(): Collection<Claim> =
        getJwtAuthenticationToken().authorities
            .mapNotNull { Claim.fromGrantedAuthority(it.authority!!) }
            .flatMap { claim ->
                // if the study has `Claim.ManageStudy` or `Claim.LimitedManageStudy`, then
                // also add `Claim.InDeployment` for all deployments in the study
                when (claim) {
                    is Claim.ManageStudy -> {
                        runBlocking {
                            participantRepository.getRecruitment(claim.studyId)
                                ?.participantGroups?.keys
                                ?.map { deploymentId -> Claim.InDeployment(deploymentId) }
                                ?.plus(claim)
                                ?: listOf(claim)
                        }
                    }

                    is Claim.LimitedManageStudy -> {
                        runBlocking {
                            participantRepository.getRecruitment(claim.studyId)
                                ?.participantGroups?.keys
                                ?.map { deploymentId -> Claim.InDeployment(deploymentId) }
                                ?.plus(claim)
                                ?: listOf(claim)
                        }
                    }

                    else -> setOf(claim)
                }
            }

    override fun getClaims(claims: Collection<KClass<out Claim>>): Collection<Claim> {
        val userClaims =
            getJwtAuthenticationToken().authorities
                .mapNotNull { Claim.fromGrantedAuthority(it.authority!!) }
        return claims.flatMap { claim ->
            when (claim) {
                Claim.InDeployment::class -> {
                    userClaims
                        .flatMap { innerClaim ->
                            when (innerClaim) {
                                is Claim.ManageStudy -> {
                                    runBlocking {
                                        participantRepository
                                            .getRecruitment(innerClaim.studyId)
                                            ?.participantGroups
                                            ?.keys
                                            ?.map { deploymentId ->
                                                Claim.InDeployment(deploymentId)
                                            }
                                            ?: emptyList()
                                    }
                                }

                                is Claim.LimitedManageStudy -> {
                                    runBlocking {
                                        participantRepository
                                            .getRecruitment(innerClaim.studyId)
                                            ?.participantGroups
                                            ?.keys
                                            ?.map { deploymentId ->
                                                Claim.InDeployment(deploymentId)
                                            }
                                            ?: emptyList()
                                    }
                                }

                                else -> listOf(innerClaim)
                            }
                        }
                }

                else ->
                    userClaims
                        .filterIsInstance(claim.java)
            }
        }
    }

    override fun getCarpIdentity(): AccountIdentity {
        val authentication = getJwtAuthenticationToken()
        val isEmailVerified: Boolean = authentication.token.getClaim("email_verified")

        return if (isEmailVerified) {
            AccountIdentity.fromEmailAddress(authentication.token.getClaim("email"))
        } else {
            AccountIdentity.fromUsername(authentication.token.getClaim("preferred_username"))
        }
    }

    /**
     * Get the [JwtAuthenticationToken] from the current security context. As the default strategy for storing
     * the security context is [SecurityContextHolder.MODE_THREADLOCAL], by default this method can only be called
     * from the thread the request originates from. If you need to call this method from a spawned thread,
     * you should start a coroutine with [SecurityCoroutineContext] to propagate the security context. You typically
     * want to do this whenever you are trying to access an authorized core service from a service layer instead of
     * a controller.
     */
    private fun getJwtAuthenticationToken(): JwtAuthenticationToken {
        val authentication = SecurityContextHolder.getContext().authentication

        checkNotNull(authentication) { "No authentication found. Are you trying to access it from a spawned thread?" }

        return authentication as JwtAuthenticationToken
    }
}
