package dk.cachet.carp.webservices.selfsignup.dto

/** Returned to the public signup caller: the magic link to redirect to immediately. */
data class SelfSignupResultDto(
    val magicLink: String,
)
