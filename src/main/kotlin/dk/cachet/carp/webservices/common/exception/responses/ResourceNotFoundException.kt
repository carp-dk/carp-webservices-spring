package dk.cachet.carp.webservices.common.exception.responses

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * The Class [ResourceNotFoundException].
 * The [ResourceNotFoundException] exception is thrown when a resource cannot be found.
 *
 * [quiet] defaults to false (preserving existing behavior for every current call site): set it to true only
 * when the not-found is an EXPECTED outcome of hitting a public, unauthenticated endpoint (e.g. self-signup
 * resolving an unknown/mistyped short code) rather than an application error - any caller can trigger that
 * on demand, so notifying per-occurrence (see ExceptionAdvices.handleNotFound) would let an attacker flood
 * the client-errors Teams channel simply by guessing codes, the same reasoning as [ConflictException.quiet].
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class ResourceNotFoundException(
    message: String?,
    val quiet: Boolean = false,
) : RuntimeException(message)
