package dk.cachet.carp.webservices.common.exception.advices

import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.context.request.ServletWebRequest

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

        verify(exactly = 0) { notificationService.sendExceptionNotification(any()) }
    }

    @Test
    fun `still notifies for a non-quiet ResourceNotFoundException, the default`() {
        // Guards the branch itself, not just the quiet path: without this, a broken condition that always
        // skips the notification would pass the test above too.
        invokeHandleNotFound(ResourceNotFoundException("Some other resource is missing."))

        verify(exactly = 1) { notificationService.sendExceptionNotification(any()) }
    }
}
