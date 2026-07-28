package dk.cachet.carp.webservices.security.authentication.oauth2

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.BulkDeleteResult
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.MagicLinkResponse
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.RequiredActions
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface IssuerFacade {
    suspend fun createAccount(account: Account): Account

    @Suppress("LongParameterList")
    suspend fun createAnonymousAccountsBulk(
        count: Int,
        expirationSeconds: Long?,
        clientId: String,
        redirectUri: String?,
        subdomain: String?,
        studyId: String?,
    ): Flow<MagicLinkResponse>

    /**
     * Examine up to [limit] members of the study group [groupId] (== study id) starting at [cursor], deleting
     * those not held back by an active session or created on/after [createdBefore] (epoch millis, a concurrent
     * generation's fresh accounts). Bounded per request even when everything is kept. Call repeatedly, passing
     * the returned [BulkDeleteResult.cursor], until [BulkDeleteResult.exhausted]; the study is done only if no
     * request reported an [BulkDeleteResult.activeSkipped].
     */
    suspend fun deleteAnonymousAccounts(
        groupId: String,
        createdBefore: Long,
        limit: Int,
        cursor: Int,
    ): BulkDeleteResult

    suspend fun getAccount(uuid: UUID): Account?

    suspend fun getAccount(identity: AccountIdentity): Account?

    suspend fun getAllByClaim(claim: Claim): List<Account>

    suspend fun updateAccount(account: Account): Account

    suspend fun deleteAccount(id: String)

    suspend fun addRole(
        account: Account,
        role: Role,
    )

    suspend fun getRoles(id: UUID): Set<Role>

    suspend fun executeActions(
        account: Account,
        redirectUri: String?,
        actions: List<RequiredActions>,
    )

    suspend fun recoverAccount(
        account: Account,
        clientId: String,
        redirectUri: String?,
        expirationSeconds: Long?,
    ): String

    suspend fun getRedirectUrisForClient(): Map<String, List<String>>

    suspend fun getCountForRole(role: Role): Long
}
