package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak

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
import io.netty.channel.ChannelOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.context.annotation.PropertySources
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.*
import org.springframework.web.util.UriBuilder
import reactor.netty.http.client.HttpClient
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant

// https://www.keycloak.org/docs-api/21.1.1/rest-api/
@Service
@PropertySources(PropertySource(value = ["classpath:config/application.yml"]))
// LongParameterList: the timeouts must be constructor-injected because the WebClients are built
// during construction, before Spring could perform field injection.
@Suppress("TooManyFunctions", "LongParameterList")
class KeycloakFacade
    @Autowired
    constructor(
        @param:Value("\${keycloak.auth-server-url}") private val authServerUrl: String,
        @param:Value("\${keycloak.realm}") private val realm: String,
        @param:Value("\${keycloak.admin.client-id}") private val clientId: String,
        @param:Value("\${keycloak.admin.client-secret}") private val clientSecret: String,
        private val environmentUtil: EnvironmentUtil,
        // Bounds on the outbound HTTP calls to Keycloak. Keycloak is co-located, so these are hang
        // catchers, not latency budgets: a healthy Keycloak answers in milliseconds, so hitting either
        // timeout means it is genuinely stuck rather than merely slow.
        @param:Value("\${keycloak.admin.connect-timeout:2s}")
        private val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
        @param:Value("\${keycloak.admin.response-timeout:30s}")
        private val responseTimeout: Duration = Duration.ofSeconds(DEFAULT_RESPONSE_TIMEOUT_SECONDS),
        // The bulk anonymous-accounts endpoint streams an NDJSON response that can legitimately run for
        // a very long time. This timeout is not an operational limit but a last-resort cleanup so a truly
        // dead connection is eventually released; a couple of days is fine given we have the resources to wait.
        @param:Value("\${keycloak.admin.bulk-response-timeout:2d}")
        private val bulkResponseTimeout: Duration = Duration.ofSeconds(DEFAULT_BULK_RESPONSE_TIMEOUT_SECONDS),
    ) : IssuerFacade {
        companion object {
            private val LOGGER: Logger = LogManager.getLogger()
            private const val INVITATION_LIFESPAN = 24 * 60 * 60 * 30 // 30 days

            // Refresh the admin token this many seconds before it actually expires, so a token fetched
            // just under the wire can't expire mid-request.
            private const val TOKEN_REFRESH_MARGIN_SECONDS = 30L

            private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 2L
            private const val DEFAULT_RESPONSE_TIMEOUT_SECONDS = 30L
            private const val DEFAULT_BULK_RESPONSE_TIMEOUT_SECONDS = 2L * 24L * 60L * 60L // 2 days
        }

        // Clock used for token-expiry timing. Production uses the system clock (the Spring constructor);
        // the secondary constructor lets tests drive expiry deterministically without affecting wiring.
        private var clock: Clock = Clock.systemUTC()

        internal constructor(
            authServerUrl: String,
            realm: String,
            clientId: String,
            clientSecret: String,
            environmentUtil: EnvironmentUtil,
            clock: Clock,
        ) : this(authServerUrl, realm, clientId, clientSecret, environmentUtil) {
            this.clock = clock
        }

        private val mapper = JsonMapper()
        private val serializationStrategies: ExchangeStrategies =
            ExchangeStrategies.builder()
                .codecs { configurer ->
                    configurer.defaultCodecs()
                        .jacksonJsonEncoder(
                            JacksonJsonEncoder(mapper, MediaType.APPLICATION_JSON),
                        )
                    configurer.defaultCodecs()
                        .jacksonJsonDecoder(
                            JacksonJsonDecoder(mapper, MediaType.APPLICATION_JSON),
                        )
                    configurer.defaultCodecs().jacksonJsonDecoder(
                        JacksonJsonDecoder(
                            mapper,
                            MediaType.APPLICATION_NDJSON,
                            MediaType.APPLICATION_JSON,
                        ),
                    )
                    configurer.defaultCodecs().jacksonJsonEncoder(
                        JacksonJsonEncoder(
                            mapper,
                            MediaType.APPLICATION_NDJSON,
                            MediaType.APPLICATION_JSON,
                        ),
                    )
                }
                .build()

        private val adminClient: WebClient = buildWebClient("$authServerUrl/admin/realms/$realm", responseTimeout)

        private val resourceClient: WebClient = buildWebClient("$authServerUrl/realms/$realm", responseTimeout)

        // Dedicated client for the anonymous-accounts endpoint: it streams an NDJSON response that can
        // legitimately run far longer than a regular call, so it uses the generous bulk timeout.
        private val bulkClient: WebClient = buildWebClient("$authServerUrl/realms/$realm", bulkResponseTimeout)

        private val authClient: WebClient =
            buildWebClient("$authServerUrl/realms/$realm", responseTimeout)
                .mutate().defaultHeaders {
                    it.contentType = MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE)
                    it.accept = listOf(MediaType.APPLICATION_JSON)
                    it.setBasicAuth(clientId, clientSecret)
                }.build()

        // Cached client_credentials admin token. Every Keycloak Admin API call needs a bearer token;
        // fetching a fresh one per call doubled the round-trips to Keycloak. The token is short-lived,
        // so we cache it until shortly before it expires. A single volatile reference bundles the token
        // with its expiry so readers never see a mismatched pair, and the mutex serializes refreshes.
        private data class CachedToken(val token: TokenResponse, val expiresAt: Instant)

        private val tokenMutex = Mutex()

        @Volatile
        private var cachedToken: CachedToken? = null

        override suspend fun createAccount(account: Account): Account {
            LOGGER.debug("Creating account {}", account)

            val userRepresentation = UserRepresentation.createFromAccount(account)

            withAdminToken { token ->
                adminClient.post().uri("/users")
                    .headers { it.setBearerAuth(token) }
                    .bodyValue(userRepresentation)
                    .retrieve()
                    .awaitBodilessEntity()
            }

            val createdAccount = getAccount(account.getIdentity())
            checkNotNull(createdAccount) { "Account not created." }

            return createdAccount
        }

        @Suppress("MagicNumber")
        override suspend fun createAnonymousAccountsBulk(
            count: Int,
            expirationSeconds: Long?,
            clientId: String,
            redirectUri: String?,
            subdomain: String?,
            studyId: String?,
        ): Flow<MagicLinkResponse> {
            // Uses the cached token but not the withAdminToken retry: this returns a cold Flow, so a 401
            // would surface during downstream collection rather than here. The cache still guarantees a
            // token with >=30s life, which is enough to have the bulk request authorized at receipt.
            val token = authenticate().accessToken

            LOGGER.debug("Creating {} anonymous accounts", count)
            val request =
                CreateAnonymousAccountsRequest(
                    count,
                    clientId,
                    redirectUri,
                    expirationSeconds?.toInt() ?: (60 * 60 * 24),
                    studyId,
                    Role.PARTICIPANT.name.lowercase(),
                    "inDeployment",
                    subdomain,
                )
            try {
                return bulkClient.post()
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
            LOGGER.debug("Updating role of account: {}", account)

            withAdminToken { token ->
                // getting role representation with id
                val roleRepresentation: RoleRepresentation =
                    adminClient.get().uri("/roles")
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .awaitBody<Set<RoleRepresentation>>()
                        .filter { it.name != null }
                        .first { it.name.equals(role.toString(), true) }

                // adding role to account
                adminClient.post().uri("/users/${account.id}/role-mappings/realm")
                    .headers { it.setBearerAuth(token) }
                    .bodyValue(listOf(roleRepresentation))
                    .retrieve()
                    .awaitBodilessEntity()
            }
        }

        override suspend fun getRoles(id: UUID): Set<Role> {
            LOGGER.debug("Getting roles of account with id: {}", id)

            val roleRepresentations =
                withAdminToken { token ->
                    adminClient.get().uri("/users/$id/role-mappings/realm")
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .awaitBody<Set<RoleRepresentation>>()
                }

            return roleRepresentations.mapNotNull { it.name }.map { Role.fromString(it) }.toSet()
        }

        override suspend fun getAccount(uuid: UUID): Account? {
            LOGGER.debug("Getting account with id: {}", uuid)

            val userRepresentation =
                withAdminToken { token ->
                    adminClient.get().uri("/users/$uuid")
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .awaitBody<UserRepresentation>()
                }

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
            LOGGER.debug("Sending execute actions email to account with id: ${account.id}")

            withAdminToken { token ->
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
                    .headers { it.setBearerAuth(token) }
                    .bodyValue(actions)
                    .retrieve()
                    .awaitBodilessEntity()
            }
        }

        override suspend fun updateAccount(account: Account): Account {
            LOGGER.debug("Updating account: {}", account)

            val userRepresentation = UserRepresentation.createFromAccount(account)

            withAdminToken { token ->
                adminClient.put().uri("/users/${account.id}")
                    .headers { it.setBearerAuth(token) }
                    .bodyValue(userRepresentation)
                    .retrieve()
                    .awaitBodilessEntity()
            }

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
                withAdminToken { token ->
                    resourceClient.post().uri("/magic-link")
                        .headers { it.setBearerAuth(token) }
                        .bodyValue(request)
                        .retrieve()
                        .awaitBody<MagicLinkResponse>()
                }

            return magicLinkResponse.link!!
        }

        override suspend fun getRedirectUrisForClient(): Map<String, List<String>> {
            LOGGER.debug("Getting redirect URIs for client.")

            val clientRepresentations =
                withAdminToken { token ->
                    adminClient.get().uri("/clients")
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .awaitBody<List<Map<String, Any>>>()
                }
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

        override suspend fun getCountForRole(role: Role): Long {
            LOGGER.debug("Getting count for users with role ${role.name}")

            return withAdminToken { token ->
                resourceClient
                    .get()
                    .uri("/analytics/users?roleName=${role.name.lowercase()}")
                    .headers { it.setBearerAuth(token) }
                    .retrieve()
                    .awaitBody<Long>()
            }
        }

        // NOTE: Keycloak's user-search endpoint silently caps each response at `max` (default 100) and we
        // don't paginate here. In practice queryAll is only used for exact lookups (0-1 hits) and for
        // getAllByClaim on study-management claims, and a study has at most a handful of staff — so the cap
        // is never reached. If a single study ever gains >100 researchers/assistants, add first/max paging
        // (and note that revokeClaimsFromAllAccounts would otherwise leave stale claims on the dropped ones).
        private suspend fun queryAll(query: String): List<Account> {
            LOGGER.debug("Querying all accounts with query: {}", query)

            val userRepresentations =
                withAdminToken { token ->
                    adminClient.get().uri("/users?$query")
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .awaitBody<List<UserRepresentation>>()
                }

            return userRepresentations.map { userRepresentation ->
                val roles = getRoles(UUID(userRepresentation.id!!))
                userRepresentation.toAccount(roles)
            }
        }

        suspend fun authenticate(): TokenResponse {
            cachedToken?.let { if (Instant.now(clock).isBefore(it.expiresAt)) return it.token }

            return tokenMutex.withLock {
                // Re-check under the lock: another coroutine may have refreshed while we waited.
                cachedToken?.let { if (Instant.now(clock).isBefore(it.expiresAt)) return@withLock it.token }

                LOGGER.debug("Fetching a new Keycloak admin token.")
                val response: TokenResponse =
                    authClient.post().uri("/protocol/openid-connect/token")
                        .bodyValue("grant_type=client_credentials")
                        .retrieve()
                        .awaitBody()

                val lifetime = (response.expiresIn?.toLong() ?: 0L) - TOKEN_REFRESH_MARGIN_SECONDS
                cachedToken = CachedToken(response, Instant.now(clock).plusSeconds(lifetime.coerceAtLeast(0)))
                response
            }
        }

        /**
         * Runs an authenticated Keycloak Admin API call with the cached token, retrying once with a fresh
         * token if the call is rejected with 401. A cached token can be rejected before its expiry if it
         * was invalidated server-side (Keycloak restart, realm-key rotation, secret rotation). A 401 means
         * the request was not processed, so re-running [block] is safe.
         */
        private suspend fun <T> withAdminToken(block: suspend (token: String) -> T): T {
            val token = requireNotNull(authenticate().accessToken) { "Keycloak returned no access token." }
            return try {
                block(token)
            } catch (e: WebClientResponseException.Unauthorized) {
                LOGGER.warn("Keycloak admin call returned 401; refreshing the admin token and retrying once.", e)
                invalidateToken(token)
                block(requireNotNull(authenticate().accessToken) { "Keycloak returned no access token." })
            }
        }

        /**
         * Clears the cached token, but only if it is still the one that was rejected - otherwise another
         * coroutine has already refreshed it and we must not discard the new token. The check-and-clear
         * runs under [tokenMutex] (the same lock refreshes hold) so it is atomic with respect to them:
         * a concurrent refresh cannot install a newer token between the check and the clear.
         */
        private suspend fun invalidateToken(rejectedToken: String) =
            tokenMutex.withLock {
                if (cachedToken?.token?.accessToken == rejectedToken) {
                    cachedToken = null
                }
            }

        private fun buildWebClient(
            baseUrl: String,
            responseTimeout: Duration,
        ): WebClient {
            val httpClient =
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
                    .responseTimeout(responseTimeout)

            return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(serializationStrategies)
                .defaultHeaders {
                    it.contentType = MediaType.APPLICATION_JSON
                    it.accept = listOf(MediaType.APPLICATION_JSON)
                }
                .build()
        }
    }
