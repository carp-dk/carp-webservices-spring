package dk.cachet.carp.webservices.selfsignup.domain

/** A study's semi-self-signup configuration; one row in `study_self_signup` per study. */
data class StudySelfSignupConfig(
    val studyId: String,
    val shortCode: String,
    val enabled: Boolean,
    val participantRoleName: String,
    val maxParticipants: Int,
    val currentParticipantCount: Int,
    val clientId: String,
    val redirectUri: String?,
    val subdomain: String?,
    val expirationSeconds: Long,
)
