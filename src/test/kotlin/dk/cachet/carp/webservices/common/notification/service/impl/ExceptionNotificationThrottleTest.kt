package dk.cachet.carp.webservices.common.notification.service.impl

import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionNotificationThrottleTest {
    private val window = Duration.ofMinutes(10)
    private var clock = Instant.parse("2026-11-11T12:00:00Z")
    private val throttle = ExceptionNotificationThrottle(window, maxGroups = 3) { clock }

    private fun advance(duration: Duration) {
        clock = clock.plus(duration)
    }

    @Nested
    inner class GroupOf {
        private fun error(
            path: String,
            exception: String = "dk.cachet.carp.webservices.common.exception.responses.ForbiddenException",
        ) = CarpErrorResponse(403, exception, "message with details", path)

        @Test
        fun `uses status, exception simple name and route`() {
            val path = "GET /api/studies/7d28651d-676d-4cf2-9e68-85686ede9cda/exports"

            assertEquals(
                "403 ForbiddenException GET /api/studies/{id}/exports",
                ExceptionNotificationThrottle.groupOf(error(path)),
            )
        }

        @Test
        fun `groups requests that differ only in ids or query string`() {
            val a = error("DELETE /api/studies/7d28651d-676d-4cf2-9e68-85686ede9cda/collections/12?force=true")
            val b = error("DELETE /api/studies/725663a5-d922-438c-8413-6351b8f92483/collections/345")

            assertEquals(ExceptionNotificationThrottle.groupOf(a), ExceptionNotificationThrottle.groupOf(b))
            assertEquals(
                "403 ForbiddenException DELETE /api/studies/{id}/collections/{id}",
                ExceptionNotificationThrottle.groupOf(a),
            )
        }

        @Test
        fun `keeps different exceptions on the same route apart`() {
            val path = "GET /api/studies/7d28651d-676d-4cf2-9e68-85686ede9cda"

            assertTrue(
                ExceptionNotificationThrottle.groupOf(error(path)) !=
                    ExceptionNotificationThrottle.groupOf(error(path, "java.lang.IllegalStateException")),
            )
        }
    }

    @Nested
    inner class TryAcquire {
        @Test
        fun `sends the first occurrence and counts repeats`() {
            assertTrue(throttle.tryAcquire("a"))
            assertFalse(throttle.tryAcquire("a"))
            assertFalse(throttle.tryAcquire("a"))

            assertEquals(mapOf("a" to 2), throttle.drainRepeats())
        }

        @Test
        fun `throttles each group separately`() {
            assertTrue(throttle.tryAcquire("a"))
            assertTrue(throttle.tryAcquire("b"))
            assertFalse(throttle.tryAcquire("a"))

            assertEquals(mapOf("a" to 1), throttle.drainRepeats())
        }

        @Test
        fun `collects new groups in one overflow group once the limit is reached`() {
            listOf("a", "b", "c").forEach { assertTrue(throttle.tryAcquire(it)) }

            // The overflow group behaves like any other: its first occurrence is sent, the rest are counted.
            assertTrue(throttle.tryAcquire("d"))
            assertFalse(throttle.tryAcquire("e"))
            assertFalse(throttle.tryAcquire("f"))
            assertFalse(throttle.tryAcquire("a"))

            assertEquals(
                mapOf("a" to 1, ExceptionNotificationThrottle.OVERFLOW_GROUP to 2),
                throttle.drainRepeats(),
            )
        }
    }

    @Nested
    inner class DrainRepeats {
        @Test
        fun `resets counts after draining`() {
            throttle.tryAcquire("a")
            throttle.tryAcquire("a")
            throttle.drainRepeats()

            assertEquals(emptyMap(), throttle.drainRepeats())
        }

        @Test
        fun `keeps throttling a group that is still repeating`() {
            throttle.tryAcquire("a")
            throttle.tryAcquire("a")
            advance(window)
            throttle.drainRepeats()

            assertFalse(throttle.tryAcquire("a"))
        }

        @Test
        fun `keeps a quiet group throttled until a full window has passed`() {
            throttle.tryAcquire("a")
            advance(Duration.ofMinutes(1))
            throttle.drainRepeats()

            assertFalse(throttle.tryAcquire("a"))
        }

        @Test
        fun `sends a group again right away after a quiet window`() {
            throttle.tryAcquire("a")
            advance(window)
            throttle.drainRepeats()

            assertTrue(throttle.tryAcquire("a"))
        }
    }
}
