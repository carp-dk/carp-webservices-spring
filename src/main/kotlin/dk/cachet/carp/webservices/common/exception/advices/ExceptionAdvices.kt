package dk.cachet.carp.webservices.common.exception.advices

import cz.jirutka.rsql.parser.RSQLParserException
import dk.cachet.carp.webservices.common.exception.responses.BadRequestException
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ForbiddenException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.exception.responses.TooManyRequestsException
import dk.cachet.carp.webservices.common.exception.responses.UnauthorizedException
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import jakarta.persistence.EntityNotFoundException
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hibernate.exception.ConstraintViolationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.util.StringUtils
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.lang.reflect.UndeclaredThrowableException

/**
 * The Data Class [CarpErrorResponse]
 * The [CarpErrorResponse] sets the error response format for all the exceptions thrown.
 */
data class CarpErrorResponse(val statusCode: Int, val exception: String, val message: String, val path: String)

/**
 * The Class [ControllerAdvice].
 * The [ControllerAdvice] apply globally to all controllers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@Suppress("TooManyFunctions")
internal class ExceptionAdvices(
    private val notificationService: INotificationService,
) : ResponseEntityExceptionHandler() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    // 400
    @ResponseBody
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Message not readable: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ResponseBody
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    override fun handleMissingServletRequestParameter(
        ex: MissingServletRequestParameterException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.localizedMessage,
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Missing request parameter: {}", request)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ResponseBody
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Argument validation failed: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ResponseBody
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(
        value = [
            DataIntegrityViolationException::class,
            BadRequestException::class,
            IllegalArgumentException::class,
            ConstraintViolationException::class,
            SerializationException::class,
            RSQLParserException::class,
        ],
    )
    protected fun handleBadRequests(
        ex: RuntimeException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Bad Request Exception: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    // 401
    @ResponseBody
    @ResponseStatus(code = HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(value = [UnauthorizedException::class])
    protected fun handleUnauthorized(
        ex: RuntimeException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Unauthorized exception: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.UNAUTHORIZED)
    }

    // 403
    @ResponseBody
    @ResponseStatus(code = HttpStatus.FORBIDDEN)
    @ExceptionHandler(value = [ForbiddenException::class])
    protected fun handleAuthenticationException(
        ex: ForbiddenException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Authentication exception: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.FORBIDDEN)
    }

    @ResponseBody
    @ResponseStatus(code = HttpStatus.FORBIDDEN)
    @ExceptionHandler(value = [AccessDeniedException::class])
    protected fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Access denied exception: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.FORBIDDEN)
    }

    // 404
    @ResponseBody
    @ResponseStatus(code = HttpStatus.NOT_FOUND)
    @ExceptionHandler(
        value = [
            UsernameNotFoundException::class,
            EntityNotFoundException::class,
            ResourceNotFoundException::class,
        ],
    )
    protected fun handleNotFound(
        ex: RuntimeException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Resource not found: {}", errorResponse)
        // See ResourceNotFoundException.quiet: skip the notification for a not-found that's an expected
        // outcome of hitting a public endpoint under its normal limits, not an application error worth
        // paging on - mirrors ConflictException.quiet's handling above.
        if (ex !is ResourceNotFoundException || !ex.quiet) {
            notificationService.sendExceptionNotification(errorResponse)
        }
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }

    // 405
    @ResponseBody
    @ResponseStatus(code = HttpStatus.METHOD_NOT_ALLOWED)
    override fun handleHttpRequestMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Request method not supported: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.METHOD_NOT_ALLOWED)
    }

    // 409
    @ResponseBody
    @ResponseStatus(code = HttpStatus.CONFLICT)
    @ExceptionHandler(
        value = [InvalidDataAccessApiUsageException::class, DataAccessException::class, ConflictException::class],
    )
    protected fun handleConflict(
        ex: RuntimeException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.CONFLICT.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Conflict exception: {}", errorResponse)
        // See ConflictException.quiet: skip the notification for conflicts that are an expected outcome of
        // hitting a public endpoint under its normal limits, not an application error worth paging on.
        if (ex !is ConflictException || !ex.quiet) {
            notificationService.sendExceptionNotification(errorResponse)
        }
        return ResponseEntity(errorResponse, HttpStatus.CONFLICT)
    }

    // 429
    @ResponseBody
    @ResponseStatus(code = HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(value = [TooManyRequestsException::class])
    protected fun handleTooManyRequests(
        ex: RuntimeException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.warn("Too many requests: {}", errorResponse)
        // Deliberately no sendExceptionNotification() here, unlike every other handler in this class: a
        // 429 is an EXPECTED outcome of rate limiting a public endpoint, not an application error worth
        // paging on. This can currently only be thrown from self-signup's unauthenticated public endpoint
        // (see TooManyRequestsException), so any caller can trigger it on demand - notifying per-occurrence
        // would let an attacker flood the client-errors Teams channel simply by exceeding the limit
        // repeatedly, and could also get the webhook itself throttled, silencing genuinely important alerts.
        return ResponseEntity(errorResponse, HttpStatus.TOO_MANY_REQUESTS)
    }

    // 500 - DEFAULT
    @ResponseBody
    @ExceptionHandler(Exception::class)
    protected fun handleUncaughtException(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("Uncaught Exception: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ResponseBody
    @ExceptionHandler(UndeclaredThrowableException::class)
    fun handleException(
        ex: UndeclaredThrowableException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            CarpErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex::class.qualifiedName.orEmpty(),
                ex.undeclaredThrowable.message.orEmpty(),
                getURIPathFromWebRequest(request),
            )
        LOGGER.error("UndeclaredThrowableException: {}", errorResponse)
        notificationService.sendExceptionNotification(errorResponse)
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun getURIPathFromWebRequest(request: WebRequest): String {
        val builder = StringBuilder()
        builder.append((request as ServletWebRequest).httpMethod.toString())
        builder.append(" ")
        builder.append(request.request.requestURI.toString())

        if (StringUtils.hasLength(request.request.queryString)) {
            builder.append("?${request.request.queryString}")
        }
        return builder.toString()
    }
}
