package dk.cachet.carp.webservices.selfsignup.dto

/** Current self-signup configuration/status for a study, for an admin UI to display (code, QR, progress). */
data class SelfSignupConfigResponseDto(
    val shortCode: String,
    val enabled: Boolean,
    val participantRoleName: String,
    val maxParticipants: Int,
    val currentParticipantCount: Int,
)
