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
import org.apache.logging.log4j.Level
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
 *
 * @property ref Reference to the matching log line and Teams message (see [ErrorContext.ref]).
 */
data class CarpErrorResponse(
    val statusCode: Int,
    val exception: String,
    val message: String,
    val path: String,
    val ref: String? = null,
)

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
        val errorResponse = report(HttpStatus.BAD_REQUEST, ex, ex.message.orEmpty(), request, "Message not readable")
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
            report(HttpStatus.BAD_REQUEST, ex, ex.localizedMessage, request, "Missing request parameter")
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
            report(HttpStatus.BAD_REQUEST, ex, ex.message.orEmpty(), request, "Argument validation failed")
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
        val errorResponse = report(HttpStatus.BAD_REQUEST, ex, ex.message.orEmpty(), request, "Bad Request Exception")
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
        val errorResponse = report(HttpStatus.UNAUTHORIZED, ex, ex.message.orEmpty(), request, "Unauthorized exception")
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
        val errorResponse = report(HttpStatus.FORBIDDEN, ex, ex.message.orEmpty(), request, "Authentication exception")
        return ResponseEntity(errorResponse, HttpStatus.FORBIDDEN)
    }

    @ResponseBody
    @ResponseStatus(code = HttpStatus.FORBIDDEN)
    @ExceptionHandler(value = [AccessDeniedException::class])
    protected fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse = report(HttpStatus.FORBIDDEN, ex, ex.message.orEmpty(), request, "Access denied exception")
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
        // See ResourceNotFoundException.quiet: skip the notification for a not-found that's an expected
        // outcome of hitting a public endpoint under its normal limits, not an application error worth
        // paging on - mirrors ConflictException.quiet's handling below.
        val errorResponse =
            report(
                HttpStatus.NOT_FOUND,
                ex,
                ex.message.orEmpty(),
                request,
                "Resource not found",
                notify = ex !is ResourceNotFoundException || !ex.quiet,
            )
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
            report(HttpStatus.METHOD_NOT_ALLOWED, ex, ex.message.orEmpty(), request, "Request method not supported")
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
        // See ConflictException.quiet: skip the notification for conflicts that are an expected outcome of
        // hitting a public endpoint under its normal limits, not an application error worth paging on.
        val errorResponse =
            report(
                HttpStatus.CONFLICT,
                ex,
                ex.message.orEmpty(),
                request,
                "Conflict exception",
                notify = ex !is ConflictException || !ex.quiet,
            )
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
            report(
                HttpStatus.TOO_MANY_REQUESTS,
                ex,
                ex.message.orEmpty(),
                request,
                "Too many requests",
                notify = false,
                level = Level.WARN,
            )
        // Deliberately no notification here, unlike every other handler in this class: a
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
            report(HttpStatus.INTERNAL_SERVER_ERROR, ex, ex.message.orEmpty(), request, "Uncaught Exception")
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ResponseBody
    @ExceptionHandler(UndeclaredThrowableException::class)
    fun handleException(
        ex: UndeclaredThrowableException,
        request: WebRequest,
    ): ResponseEntity<CarpErrorResponse> {
        val errorResponse =
            report(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.undeclaredThrowable,
                ex.undeclaredThrowable.message.orEmpty(),
                request,
                "UndeclaredThrowableException",
                exceptionName = ex::class.qualifiedName.orEmpty(),
            )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    /**
     * Builds the [CarpErrorResponse] for [ex], logs it with its [ErrorContext] (with the stack trace for server
     * errors), and posts it to Teams unless [notify] is false.
     */
    @Suppress("LongParameterList")
    private fun report(
        status: HttpStatus,
        ex: Throwable,
        message: String,
        request: WebRequest,
        label: String,
        notify: Boolean = true,
        level: Level = Level.ERROR,
        exceptionName: String = ex::class.qualifiedName.orEmpty(),
    ): CarpErrorResponse {
        val context = ErrorContext.of(request)
        val errorResponse =
            CarpErrorResponse(status.value(), exceptionName, message, getURIPathFromWebRequest(request), context.ref)
        if (status.is5xxServerError) {
            LOGGER.log(level, "{} [{}]: {}", label, context, errorResponse, ex)
        } else {
            LOGGER.log(level, "{} [{}]: {}", label, context, errorResponse)
        }
        if (notify) notificationService.sendExceptionNotification(errorResponse, context)
        return errorResponse
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
