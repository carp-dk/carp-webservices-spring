package dk.cachet.carp.webservices.account.service.impl

import dk.cachet.carp.common.application.EmailAddress
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.MagicLinkResponse
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.RequiredActions
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.flow.Flow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class AccountServiceImpl(
    private val issuerFacade: IssuerFacade,
) : AccountService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun invite(
        identity: AccountIdentity,
        role: Role,
        redirectUri: String?,
    ): Account {
        var isNewAccount = false
        var account = findByAccountIdentity(identity)

        if (account == null) {
            isNewAccount = true
            account = issuerFacade.createAccount(Account.fromAccountIdentity(identity))
            LOGGER.info("User created for account identity: $identity")
        }

        LOGGER.info("Adding role: $role for user: $identity")
        issuerFacade.addRole(account, role)
        account.role = role

        if (isNewAccount && !account.email.isNullOrBlank()) {
            try {
                issuerFacade.executeActions(account, redirectUri, RequiredActions.forNewAccounts)
            } catch (e: WebClientResponseException) {
                LOGGER.error(
                    "Failed to send an email with actions: $identity",
                    e,
                )
            }
        }

        return account
    }

    override suspend fun findByUUID(uuid: UUID): Account? {
        val result = runCatching { issuerFacade.getAccount(uuid) }.getOrNull()

        if (result == null) {
            LOGGER.warn("Account not found for UUID: $uuid")
        }

        return result
    }

    override suspend fun findByAccountIdentity(identity: AccountIdentity): Account? {
        val result = runCatching { issuerFacade.getAccount(identity) }.getOrNull()

        if (result == null) {
            LOGGER.warn("Account not found for identity: $identity")
        }
        return result
    }

    override suspend fun findAllByClaim(claim: Claim): List<Account> {
        val result = runCatching { issuerFacade.getAllByClaim(claim) }.getOrNull()

        if (result == null) {
            LOGGER.warn("No accounts found for claim: $claim")
            return emptyList()
        }

        return result
    }

    override suspend fun hasRoleByEmail(
        email: EmailAddress,
        role: Role,
    ): Boolean {
        val account = findByAccountIdentity(AccountIdentity.fromEmailAddress(email.address))

        requireNotNull(account)

        return account.role!! >= role
    }

    override suspend fun addRole(
        identity: AccountIdentity,
        role: Role,
    ) {
        val account = findByAccountIdentity(identity)

        requireNotNull(account)

        LOGGER.info("Adding role: $role for user: $identity")
        issuerFacade.addRole(account, role)
    }

    override suspend fun grant(
        identity: AccountIdentity,
        claims: Set<Claim>,
    ): Account {
        if (claims.any { it is Claim.VirtualClaim }) {
            throw UnsupportedOperationException("Virtual claims cannot be granted.")
        }

        val account = findByAccountIdentity(identity)

        requireNotNull(account)

        LOGGER.info("Granting claim: $claims for user: $identity")
        account.carpClaims = account.carpClaims?.plus(claims) ?: claims
        return issuerFacade.updateAccount(account)
    }

    override suspend fun revoke(
        identity: AccountIdentity,
        claims: Set<Claim>,
    ): Account {
        if (claims.any { it is Claim.VirtualClaim }) {
            throw UnsupportedOperationException("Virtual claims cannot be revoked.")
        }

        val account = findByAccountIdentity(identity)

        requireNotNull(account)

        LOGGER.info("Revoking claim: $claims for user: $identity")
        account.carpClaims = account.carpClaims?.minus(claims) ?: emptySet()
        return issuerFacade.updateAccount(account)
    }

    override suspend fun generateAnonymousAccount(
        expirationSeconds: Long?,
        clientId: String,
        redirectUri: String?,
        subdomain: String?,
        numberOfAccounts: Int,
        studyId: String?,
    ): Flow<MagicLinkResponse> =
        issuerFacade.createAnonymousAccounts(
            numberOfAccounts,
            expirationSeconds,
            clientId,
            redirectUri,
            subdomain,
            studyId,
        )

    override suspend fun getRedirectUris(): Map<String, List<String>> {
        return issuerFacade.getRedirectUrisForClient()
    }
}
