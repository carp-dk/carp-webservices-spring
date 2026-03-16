package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain

data class CreateAnonymousAccountsRequest(
    val count: Int,
    val clientId: String,
    val redirectUri: String?,
    val validitySeconds: Int?,
    val groupId: String?,
    val roleName: String?,
    val attributeKey: String?,
    val subdomain: String?,
)
