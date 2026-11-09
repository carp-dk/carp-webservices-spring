package dk.cachet.carp.webservices.admin.dto

import java.time.Instant

data class AdminResearcherDto(
    val id: String,
    val username: String?,
    val email: String?,
    val fullName: String?,
    val enabled: Boolean,
    val emailVerified: Boolean,
    /** When the Keycloak account was created. */
    val createdAt: Instant?,
    /** Studies this researcher owns; studies they only manage through a claim are not counted. */
    val ownedStudyCount: Long,
    val liveStudyCount: Long,
)
