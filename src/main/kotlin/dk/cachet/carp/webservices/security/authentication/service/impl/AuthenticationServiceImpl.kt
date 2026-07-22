package dk.cachet.carp.webservices.security.authentication.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import kotlin.collections.mapNotNull

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

    override fun hasClaim(claim: Claim): Boolean {
        val claims = getClaims()
        if (claim in claims) return true

        // A study manager implicitly has access to every deployment in the study. Rather than
        // expanding a ManageStudy/LimitedManageStudy claim into an InDeployment claim per deployment
        // (which would load the whole recruitment snapshot into memory on every authorization check),
        // resolve the single deployment's study and check whether the user manages it. carp.core keeps
        // deployments free of a study reference by design; this repo does not need that limitation.
        if (claim is Claim.InDeployment) {
            val managedStudyIds = claims.managedStudyIds()
            if (managedStudyIds.isEmpty()) return false

            val studyId = participantRepository.getStudyIdByDeploymentId(claim.deploymentId) ?: return false
            return studyId in managedStudyIds
        }

        return false
    }

    private fun Collection<Claim>.managedStudyIds(): Set<UUID> =
        buildSet {
            this@managedStudyIds.forEach {
                when (it) {
                    is Claim.ManageStudy -> add(it.studyId)
                    is Claim.LimitedManageStudy -> add(it.studyId)
                    else -> {}
                }
            }
        }

    override fun getCarpIdentity(): AccountIdentity {
        val authentication = getJwtAuthenticationToken()
        // Spring Security 7.1 made ClaimAccessor.getClaim nullable (T?); a missing claim now returns
        // null instead of NPE-ing on a platform type, so handle absence explicitly.
        val isEmailVerified: Boolean = authentication.token.getClaim<Boolean>("email_verified") ?: false

        return if (isEmailVerified) {
            AccountIdentity.fromEmailAddress(
                requireNotNull(authentication.token.getClaim<String>("email")) { "JWT is missing the 'email' claim" },
            )
        } else {
            AccountIdentity.fromUsername(
                requireNotNull(authentication.token.getClaim<String>("preferred_username")) {
                    "JWT is missing the 'preferred_username' claim"
                },
            )
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
