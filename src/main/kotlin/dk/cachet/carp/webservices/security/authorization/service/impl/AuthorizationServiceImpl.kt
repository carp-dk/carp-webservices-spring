package dk.cachet.carp.webservices.security.authorization.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.exception.responses.ForbiddenException
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import org.springframework.stereotype.Service

@Service
class AuthorizationServiceImpl(
    private val authenticationService: AuthenticationService,
    private val accountService: AccountService,
) : AuthorizationService {
    companion object {
        // TODO: move to resources
        private const val PERMISSION_DENIED_MSG = "Permission denied or resource not found."
    }

    override fun require(claims: Set<Claim>) = require(*claims.toTypedArray()) { PERMISSION_DENIED_MSG }

    override fun require(claim: Claim) = require(claim) { PERMISSION_DENIED_MSG }

    private inline fun require(
        vararg claims: Claim,
        crossinline lazyMessage: () -> Any = {},
    ) {
        if (isAdmin()) return

        require(
            authenticationService.getClaims(claims.mapTo(HashSet()) { it::class }).containsAll(claims.toList()),
            lazyMessage,
        )
    }

    override fun requireAnyClaim(claims: Set<Claim>) = requireAnyClaim(*claims.toTypedArray()) { PERMISSION_DENIED_MSG }

    private inline fun requireAnyClaim(
        vararg claims: Claim,
        crossinline lazyMessage: () -> Any = {},
    ) {
        if (isAdmin()) return

        val currentClaims = authenticationService.getClaims(claims.mapTo(HashSet()) { it::class })
        require(claims.any { currentClaims.contains(it) }, lazyMessage)
    }

    override fun require(role: Role) = require(role) { PERMISSION_DENIED_MSG }

    private inline fun require(
        role: Role,
        crossinline lazyMessage: () -> Any = {},
    ) {
        if (isAdmin()) return

        require(authenticationService.getRole() >= role, lazyMessage)
    }

    override fun requireAnyRole(roles: Set<Role>) {
        requireAnyRole(*roles.toTypedArray()) { PERMISSION_DENIED_MSG }
    }

    private inline fun requireAnyRole(
        vararg roles: Role,
        crossinline lazyMessage: () -> Any = {},
    ) {
        if (isAdmin()) return

        val currentRole = authenticationService.getRole()
        require(roles.any { currentRole >= it }, lazyMessage)
    }

    override fun requireOwner(ownerId: UUID) = requireOwner(ownerId) { PERMISSION_DENIED_MSG }

    private inline fun requireOwner(
        ownerId: UUID,
        crossinline lazyMessage: () -> Any = {},
    ) {
        if (isAdmin()) return

        require(authenticationService.getId() == ownerId, lazyMessage)
    }

    override suspend fun grantCurrentAuthentication(claim: Claim) = grantCurrentAuthentication(setOf(claim))

    override suspend fun grantCurrentAuthentication(claims: Set<Claim>) {
        accountService.grant(authenticationService.getCarpIdentity(), claims)
    }

    override suspend fun revokeClaimFromAllAccounts(claim: Claim) = revokeClaimsFromAllAccounts(setOf(claim))

    override suspend fun revokeClaimsFromAllAccounts(claims: Set<Claim>) {
        claims.forEach { claim ->
            accountService.findAllByClaim(claim).forEach {
                accountService.revoke(it.getIdentity(), claims)
            }
        }
    }

    private fun isAdmin() = authenticationService.getRole() == Role.SYSTEM_ADMIN

    private inline fun require(
        value: Boolean,
        lazyMessage: () -> Any,
    ) {
        if (!value) {
            val message = lazyMessage()
            throw ForbiddenException(message.toString())
        }
    }
}
