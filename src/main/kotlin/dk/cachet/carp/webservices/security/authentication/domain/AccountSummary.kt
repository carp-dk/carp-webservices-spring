package dk.cachet.carp.webservices.security.authentication.domain

import java.time.Instant

/** Read-only account details from the identity provider, without roles or claims (used for listings). */
data class AccountSummary(
    val id: String,
    val username: String?,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val createdAt: Instant?,
)
