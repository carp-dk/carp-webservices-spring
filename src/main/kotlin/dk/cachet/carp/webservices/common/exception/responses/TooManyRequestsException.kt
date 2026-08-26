package dk.cachet.carp.webservices.common.exception.responses

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * The Class [TooManyRequestsException].
 * The [TooManyRequestsException] with a [HttpStatus.TOO_MANY_REQUESTS] is thrown when a client exceeds a
 * rate limit.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
class TooManyRequestsException(message: String?) : RuntimeException(message)
