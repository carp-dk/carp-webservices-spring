package dk.cachet.carp.webservices.common.exception.responses

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * The Class [ConflictException].
 * The [ConflictException] with a [HttpStatus.CONFLICT] is thrown if the request is conflicting with the state of the
 * server.
 *
 * [quiet] defaults to false (preserving existing behavior for every current call site): set it to true only
 * when the conflict is an EXPECTED outcome of hitting a public, unauthenticated endpoint under its normal,
 * documented limits (e.g. self-signup's "study full"/"study not live") rather than an application error -
 * any caller can trigger those on demand, so notifying per-occurrence (see ExceptionAdvices.handleConflict)
 * would let an attacker flood the client-errors Teams channel simply by exercising the endpoint normally.
 */
@ResponseStatus(HttpStatus.CONFLICT)
class ConflictException(
    message: String?,
    val quiet: Boolean = false,
) : RuntimeException(message)
