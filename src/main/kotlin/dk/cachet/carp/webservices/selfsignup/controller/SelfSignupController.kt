package dk.cachet.carp.webservices.selfsignup.controller

import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.selfsignup.controller.SelfSignupController.Companion.SELF_SIGNUP_PUBLIC
import dk.cachet.carp.webservices.selfsignup.dto.SelfSignupResultDto
import dk.cachet.carp.webservices.selfsignup.service.SelfSignupPublicService
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public, unauthenticated self-signup by short code (see the `permit-all` entry for [SELF_SIGNUP_PUBLIC] in
 * application.yml - the only mutating, non-authenticated endpoint in this codebase). Deliberately `POST`,
 * not `GET`: a plain browser reload/prefetch on a `GET` would silently mint a second account. The
 * QR/link a participant scans should point at a small landing page that issues this `POST` client-side and
 * then redirects to the returned magic link.
 */
@RestController
@RequestMapping(SELF_SIGNUP_PUBLIC)
class SelfSignupController(
    private val selfSignupPublicService: SelfSignupPublicService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        const val SELF_SIGNUP_PUBLIC = "/api/self-signup/{${PathVariableName.SHORT_CODE}}"
    }

    @Operation(
        description =
            "Public, unauthenticated self-signup by short code. Creates one new anonymous account, " +
                "deployment, and participant for the study the code belongs to, and returns its magic " +
                "link. Fails with 404 for an unknown code, 409 if self-signup is disabled or full, or " +
                "429 if the caller's address is sending too many requests.",
    )
    @PostMapping
    fun signUp(
        @PathVariable(PathVariableName.SHORT_CODE) shortCode: String,
        request: HttpServletRequest,
    ): SelfSignupResultDto {
        LOGGER.info("Start POST: $SELF_SIGNUP_PUBLIC")
        // A plain (non-suspend) handler, matching the convention elsewhere in this codebase for endpoints
        // that need the raw HttpServletRequest (see DocumentController); bridges into the suspend service
        // with runBlocking, the same way the scheduled cleanup jobs do.
        return runBlocking { selfSignupPublicService.signUp(shortCode, request.remoteAddr) }
    }
}
