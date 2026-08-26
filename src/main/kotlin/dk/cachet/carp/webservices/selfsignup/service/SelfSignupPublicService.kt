package dk.cachet.carp.webservices.selfsignup.service

import dk.cachet.carp.webservices.selfsignup.dto.SelfSignupResultDto

/** Public, unauthenticated self-signup by short code. */
interface SelfSignupPublicService {
    /**
     * Sign up one new anonymous participant for the study whose self-signup code is [rawCode], rate
     * limited per [clientIp]. Returns the magic link to redirect the caller to.
     */
    suspend fun signUp(
        rawCode: String,
        clientIp: String,
    ): SelfSignupResultDto
}
