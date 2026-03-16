package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain

data class CreateAnonymousAccountsResponse(
    val username: String,
    val magicLink: String,
)
