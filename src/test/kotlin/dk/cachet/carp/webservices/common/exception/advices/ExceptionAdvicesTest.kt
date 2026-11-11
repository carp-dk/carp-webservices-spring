package dk.cachet.carp.webservices.common.exception.advices

import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.assertEquals

/**
 * `handleNotFound` is `protected` (Spring's `@ExceptionHandler` methods only need to be visible to the
 * dispatcher, which calls them reflectively), so it's invoked here via reflection rather than a subclass -
 * [ExceptionAdvices] itself is a final class, so a test subclass isn't an option either.
 */
class ExceptionAdvicesTest {
    private val notificationService: INotificationService = mockk(relaxed = true)
    private val advices = ExceptionAdvices(notificationService)

    private fun mockWebRequest(): ServletWebRequest {
        val servletRequest: HttpServletRequest =
            mockk {
                every { requestURI } returns "/api/self-signup/ABCDE"
                every { queryString } returns null
            }
        return mockk<ServletWebRequest> {
            every { httpMethod } returns HttpMethod.POST
            every { request } returns servletRequest
        }
    }

    private fun invokeHandleNotFound(ex: RuntimeException) {
        val method =
            ExceptionAdvices::class.java.getDeclaredMethod(
                "handleNotFound",
                RuntimeException::class.java,
                org.springframework.web.context.request.WebRequest::class.java,
            )
        method.isAccessible = true
        method.invoke(advices, ex, mockWebRequest())
    }

    @Test
    fun `does not notify for a quiet ResourceNotFoundException`() {
        invokeHandleNotFound(ResourceNotFoundException("Unknown self-signup code.", quiet = true))

        verify(exactly = 0) { notificationService.sendExceptionNotification(any(), any()) }
    }

    @Test
    fun `still notifies for a non-quiet ResourceNotFoundException, the default`() {
        // Guards the branch itself, not just the quiet path: without this, a broken condition that always
        // skips the notification would pass the test above too.
        invokeHandleNotFound(ResourceNotFoundException("Some other resource is missing."))

        verify(exactly = 1) { notificationService.sendExceptionNotification(any(), any()) }
    }

    @Test
    fun `returns the reference and notifies with the request context`() {
        val request =
            ServletWebRequest(
                MockHttpServletRequest("GET", "/api/studies/s-1/exports").apply {
                    setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("study-id" to "s-1"))
                },
            )
        val method =
            ExceptionAdvices::class.java.getDeclaredMethod(
                "handleUncaughtException",
                Exception::class.java,
                org.springframework.web.context.request.WebRequest::class.java,
            )
        method.isAccessible = true
        val context = slot<ErrorContext>()
        every { notificationService.sendExceptionNotification(any(), capture(context)) } returns Unit

        @Suppress("UNCHECKED_CAST")
        val response =
            method.invoke(advices, IllegalStateException("Boom"), request) as ResponseEntity<CarpErrorResponse>

        assertEquals(500, response.body?.statusCode)
        assertEquals("Boom", response.body?.message)
        assertEquals(context.captured.ref, response.body?.ref)
        assertEquals(mapOf("study-id" to "s-1"), context.captured.ids)
    }
}
