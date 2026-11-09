package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserRepresentation(
    var id: String? = null,
    var username: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var email: String? = null,
    var enabled: Boolean? = true,
    var attributes: Map<String, Any>? = emptyMap(),
) {
    companion object {
        fun createFromAccount(account: Account) =
            UserRepresentation(
                id = account.id,
                username = account.username ?: account.email,
                firstName = account.firstName,
                lastName = account.lastName,
                email = account.email,
                attributes = account.carpClaims?.groupBy({ Claim.userAttributeName(it::class) }, { it.value }),
            )
    }

    fun toAccount(roles: Set<Role>) =
        Account(
            id = id,
            username = username,
            firstName = firstName,
            lastName = lastName,
            email = email,
            role = roles.max(),
            carpClaims = attributes?.mapNotNull { Claim.fromUserAttribute(it.key to it.value) }?.flatten()?.toSet(),
        )
}

/**
 * The read-only user record Keycloak returns from listings with `briefRepresentation=true`. Kept apart from
 * [UserRepresentation] because that one is also written back to Keycloak.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BriefUserRepresentation(
    var id: String? = null,
    var username: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var email: String? = null,
    var enabled: Boolean? = null,
    var emailVerified: Boolean? = null,
    /** Epoch milliseconds. */
    var createdTimestamp: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoleRepresentation(
    var id: String? = null,
    var name: String? = null,
)

enum class RequiredActions {
    VERIFY_EMAIL,
    UPDATE_PROFILE,
    UPDATE_PASSWORD,
    CONFIGURE_TOTP,
    TERMS_AND_CONDITIONS,
    ;

    companion object {
        val forNewAccounts =
            listOf(
                VERIFY_EMAIL,
                UPDATE_PASSWORD,
                UPDATE_PROFILE,
            )
    }
}
