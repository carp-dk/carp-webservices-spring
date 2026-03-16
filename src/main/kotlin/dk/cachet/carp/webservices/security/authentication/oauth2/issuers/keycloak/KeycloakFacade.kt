package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak

import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.webservices.common.environment.EnvironmentProfile
import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.*
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.reactive.asFlow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.context.annotation.PropertySources
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.*
import org.springframework.web.util.UriBuilder

// https://www.keycloak.org/docs-api/21.1.1/rest-api/
@Service
@PropertySources(PropertySource(value = ["classpath:config/application.yml"]))
@Suppress("TooManyFunctions")
class KeycloakFacade(
    @Value("\${keycloak.auth-server-url}") private val authServerUrl: String,
    @Value("\${keycloak.realm}") private val realm: String,
    @Value("\${keycloak.admin.client-id}") private val clientId: String,
    @Value("\${keycloak.admin.client-secret}") private val clientSecret: String,
    private val objectMapper: ObjectMapper,
    private val environmentUtil: EnvironmentUtil,
) : IssuerFacade {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val INVITATION_LIFESPAN = 24 * 60 * 60 * 30 // 30 days
    }

    private val serializationStrategies: ExchangeStrategies =
        ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs()
                    .jackson2JsonEncoder(
                        Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON),
                    )
                configurer.defaultCodecs()
                    .jackson2JsonDecoder(
                        Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON),
                    )
                configurer.defaultCodecs().jackson2JsonDecoder(
                    Jackson2JsonDecoder(
                        objectMapper,
                        MediaType.APPLICATION_NDJSON,
                        MediaType.APPLICATION_JSON,
                    ),
                )
                configurer.defaultCodecs().jackson2JsonEncoder(
                    Jackson2JsonEncoder(
                        objectMapper,
                        MediaType.APPLICATION_NDJSON,
                        MediaType.APPLICATION_JSON,
                    ),
                )
            }
            .build()

    private val adminClient: WebClient = buildWebClient("$authServerUrl/admin/realms/$realm")

    private val resourceClient: WebClient = buildWebClient("$authServerUrl/realms/$realm")

    private val authClient: WebClient =
        buildWebClient("$authServerUrl/realms/$realm")
            .mutate().defaultHeaders {
                it.contentType = MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE)
                it.accept = listOf(MediaType.APPLICATION_JSON)
                it.setBasicAuth(clientId, clientSecret)
            }.build()

    override suspend fun createAccount(account: Account): Account {
        val token = authenticate().accessToken

        LOGGER.debug("Creating account {}", account)

        val userRepresentation = UserRepresentation.createFromAccount(account)

        adminClient.post().uri("/users")
            .headers {
                it.setBearerAuth(token!!)
            }
            .bodyValue(userRepresentation)
            .retrieve()
            .awaitBodilessEntity()

        val createdAccount = getAccount(account.getIdentity())
        checkNotNull(createdAccount) { "Account not created." }

        return createdAccount
    }

    @Suppress("MagicNumber")
    override suspend fun createAnonymousAccounts(
        count: Int,
        expirationSeconds: Long?,
        clientId: String,
        redirectUri: String?,
        subdomain: String?,
        studyId: String?,
    ): Flow<MagicLinkResponse> {
        val token = authenticate().accessToken

        LOGGER.debug("Creating {} anonymous accounts", count)
        val request =
            CreateAnonymousAccountsRequest(
                count,
                clientId,
                redirectUri,
                expirationSeconds?.toInt() ?: (60 * 60 * 24),
                studyId,
                Role.PARTICIPANT.name,
                "inDeployment",
                subdomain,
            )
        try {
            return resourceClient.post()
                .uri("/bulk-users/anonymous")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .bodyValue(request)
                .accept(MediaType.APPLICATION_NDJSON)
                .retrieve()
                .bodyToFlux(MagicLinkResponse::class.java)
                .asFlow()
                .buffer(1000)
        } catch (e: WebClientResponseException) {
            LOGGER.error("Error creating anonymous accounts: ${e.statusCode} - ${e.responseBodyAsString}")
            LOGGER.error(e.message)
            LOGGER.error(e.cause)
            throw e
        }
    }

    override suspend fun addRole(
        account: Account,
        role: Role,
    ) {
        val token = authenticate().accessToken

        LOGGER.debug("Updating role of account: {}", account)

        // getting role representation with id
        val roleRepresentation: RoleRepresentation =
            adminClient.get().uri("/roles")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .retrieve()
                .awaitBody<Set<RoleRepresentation>>()
                .filter { it.name != null }
                .first { it.name.equals(role.toString(), true) }

        // adding role to account
        adminClient.post().uri("/users/${account.id}/role-mappings/realm")
            .headers {
                it.setBearerAuth(token!!)
            }
            .bodyValue(listOf(roleRepresentation))
            .retrieve()
            .awaitBodilessEntity()
    }

    override suspend fun getRoles(id: UUID): Set<Role> {
        val token = authenticate().accessToken

        LOGGER.debug("Getting roles of account with id: {}", id)

        val roleRepresentations =
            adminClient.get().uri("/users/$id/role-mappings/realm")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .retrieve()
                .awaitBody<Set<RoleRepresentation>>()

        return roleRepresentations.mapNotNull { it.name }.map { Role.fromString(it) }.toSet()
    }

    override suspend fun getAccount(uuid: UUID): Account? {
        val token = authenticate().accessToken

        LOGGER.debug("Getting account with id: {}", uuid)

        val userRepresentation =
            adminClient.get().uri("/users/$uuid")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .retrieve()
                .awaitBody<UserRepresentation>()

        val roles = getRoles(uuid)
        return userRepresentation.toAccount(roles)
    }

    override suspend fun getAccount(identity: AccountIdentity): Account? {
        val queryString =
            when (identity) {
                is EmailAccountIdentity -> "email=${identity.emailAddress}"
                is UsernameAccountIdentity -> "username=${identity.username}"
                else -> throw IllegalArgumentException(
                    "Unsupported account identity type: ${identity::class.simpleName}.",
                )
            }.plus("&exact=true")

        LOGGER.debug("Getting account with identity: {}", identity)

        return queryAll(queryString).firstOrNull()
    }

    override suspend fun getAllByClaim(claim: Claim): List<Account> {
        val queryString = "q=${Claim.userAttributeName(claim::class)}:${claim.value}"

        LOGGER.debug("Getting all accounts with claim: {}", claim)

        return queryAll(queryString)
    }

    override suspend fun executeActions(
        account: Account,
        redirectUri: String?,
        actions: List<RequiredActions>,
    ) {
        val token = authenticate().accessToken

        LOGGER.debug("Sending execute actions email to account with id: ${account.id}")

        adminClient.put().uri("/users/${account.id}/execute-actions-email") { uriBuilder: UriBuilder ->
            var builder =
                uriBuilder
                    .queryParam("client_id", clientId)
                    .queryParam("lifespan", INVITATION_LIFESPAN)

            if (environmentUtil.profile != EnvironmentProfile.LOCAL) {
                builder = builder.queryParam("redirect_uri", redirectUri ?: environmentUtil.portalUrl)
            }

            builder.build()
        }
            .headers {
                it.setBearerAuth(token!!)
            }
            .bodyValue(actions)
            .retrieve()
            .awaitBodilessEntity()
    }

    override suspend fun updateAccount(account: Account): Account {
        val token = authenticate().accessToken

        LOGGER.debug("Updating account: {}", account)

        val userRepresentation = UserRepresentation.createFromAccount(account)

        adminClient.put().uri("/users/${account.id}")
            .headers {
                it.setBearerAuth(token!!)
            }
            .bodyValue(userRepresentation)
            .retrieve()
            .awaitBodilessEntity()

        return account
    }

    override suspend fun deleteAccount(id: String) {
        throw UnsupportedOperationException("Account deletion is not supported by Carp Webservices.")
    }

    override suspend fun recoverAccount(
        account: Account,
        clientId: String,
        redirectUri: String?,
        expirationSeconds: Long?,
    ): String {
        val token = authenticate().accessToken

        LOGGER.debug("Generating recovery link for account: {}", account)

        val request =
            MagicLinkRequest(
                account.email,
                account.username,
                clientId,
                expirationSeconds,
                redirectUri,
            )

        val magicLinkResponse =
            resourceClient.post().uri("/magic-link")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .bodyValue(request)
                .retrieve()
                .awaitBody<MagicLinkResponse>()

        return magicLinkResponse.link!!
    }

    override suspend fun getRedirectUrisForClient(): Map<String, List<String>> {
        val token = authenticate().accessToken

        LOGGER.debug("Getting redirect URIs for client.")

        val clientRepresentations =
            adminClient.get().uri("/clients")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .retrieve()
                .awaitBody<List<Map<String, Any>>>()
                // Only include clients that can be used for logging in (i.e. display in console).
                .filter { it["alwaysDisplayInConsole"] == true }

        val clientsWithRedirectUris: Map<String, List<String>> =
            clientRepresentations
                .filter { it["redirectUris"] != null && (it["redirectUris"] as? List<*>)?.isNotEmpty() == true }
                .mapNotNull { clientRepresentation ->
                    val id = clientRepresentation["clientId"] as? String
                    val redirectUris = clientRepresentation["redirectUris"] as? List<*>
                    if (id != null && redirectUris != null) {
                        id to redirectUris.filterIsInstance<String>()
                    } else {
                        null
                    }
                }.toMap()
        return clientsWithRedirectUris
    }

    private suspend fun queryAll(query: String): List<Account> {
        val token = authenticate().accessToken

        LOGGER.debug("Querying all accounts with query: {}", query)

        val userRepresentations =
            adminClient.get().uri("/users?$query")
                .headers {
                    it.setBearerAuth(token!!)
                }
                .retrieve()
                .awaitBody<List<UserRepresentation>>()

        return userRepresentations.map { userRepresentation ->
            val roles = getRoles(UUID(userRepresentation.id!!))
            userRepresentation.toAccount(roles)
        }
    }

    suspend fun authenticate(): TokenResponse =
        authClient.post().uri("/protocol/openid-connect/token")
            .bodyValue("grant_type=client_credentials")
            .retrieve()
            .awaitBody()

    private fun buildWebClient(baseUrl: String): WebClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .exchangeStrategies(serializationStrategies)
            .defaultHeaders {
                it.contentType = MediaType.APPLICATION_JSON
                it.accept = listOf(MediaType.APPLICATION_JSON)
            }
            .build()
}
