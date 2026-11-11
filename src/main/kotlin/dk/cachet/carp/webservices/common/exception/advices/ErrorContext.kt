package dk.cachet.carp.webservices.common.exception.advices

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter
import java.lang.reflect.Type
import java.util.UUID

/**
 * Where an error happened, so its log line and Teams message point at the user, study and deployment involved.
 *
 * @property ref Short random reference, also returned to the client as [CarpErrorResponse.ref], to match a
 *   user's report to the log.
 * @property userId The authenticated account id (the JWT subject), if any.
 * @property requestType The core service request type for RPC endpoints, e.g. `GetStudyDeploymentStatus`.
 * @property ids Ids from the URL (`study-id`, `deployment-id`, ...) and from the top level of an RPC request
 *   body (`studyId`, `studyDeploymentId`, ...).
 */
data class ErrorContext(
    val ref: String,
    val userId: String? = null,
    val requestType: String? = null,
    val ids: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        (
            listOfNotNull("ref=$ref", userId?.let { "user=$it" }, requestType?.let { "request=$it" }) +
                ids.map { (name, value) -> "$name=$value" }
        ).joinToString(", ")

    companion object {
        /** Request attribute holding the captured request body, see [RequestBodyCapture]. */
        internal const val BODY_ATTRIBUTE = "dk.cachet.carp.webservices.errorContext.body"

        private const val REF_LENGTH = 8
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Collects the context of the failed [request]. Never throws: an error handler must still respond when
         * part of the context can't be read, so each part falls back to absent.
         */
        fun of(request: WebRequest): ErrorContext {
            val ref = UUID.randomUUID().toString().take(REF_LENGTH)
            val body = runCatching { requestBodyIds(request) }.getOrNull()
            return ErrorContext(
                ref = ref,
                userId = runCatching { userIdOf(request) }.getOrNull(),
                requestType = body?.first,
                ids = runCatching { pathIds(request) }.getOrDefault(emptyMap()) + body?.second.orEmpty(),
            )
        }

        private fun userIdOf(request: WebRequest): String? {
            val principal = (request as? ServletWebRequest)?.request?.userPrincipal ?: request.userPrincipal
            return (principal as? JwtAuthenticationToken)?.token?.subject
        }

        @Suppress("UNCHECKED_CAST")
        private fun pathIds(request: WebRequest): Map<String, String> =
            request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST,
            ) as? Map<String, String> ?: emptyMap()

        /** The request type and top-level `...Id` fields of a captured JSON body, if there is one. */
        private fun requestBodyIds(request: WebRequest): Pair<String?, Map<String, String>>? {
            val body = request.getAttribute(BODY_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? String ?: return null
            val json = JSON.parseToJsonElement(body) as? JsonObject ?: return null
            val type = (json["__type"] as? JsonPrimitive)?.content?.substringAfterLast('.')
            val ids =
                json
                    .filterKeys { it.endsWith("Id") }
                    .mapNotNull { (key, value) ->
                        (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
                    }.toMap()
            return type to ids
        }
    }
}

/**
 * Keeps `String` request bodies (the core RPC endpoints) as a request attribute, so [ErrorContext] can read their
 * ids if the request fails. Only a reference is stored, and bodies above [MAX_BODY_LENGTH] (data uploads) are
 * skipped, so nothing large is ever parsed on the error path.
 */
@ControllerAdvice
class RequestBodyCapture : RequestBodyAdviceAdapter() {
    companion object {
        const val MAX_BODY_LENGTH = 64 * 1024
    }

    override fun supports(
        methodParameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = targetType == String::class.java

    override fun afterBodyRead(
        body: Any,
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Any {
        if (body is String && body.length <= MAX_BODY_LENGTH) {
            RequestContextHolder.getRequestAttributes()
                ?.setAttribute(ErrorContext.BODY_ATTRIBUTE, body, RequestAttributes.SCOPE_REQUEST)
        }
        return body
    }
}
