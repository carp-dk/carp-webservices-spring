package dk.cachet.carp.webservices.common.notification.service.impl

import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Groups repeated exception notifications so a burst of the same error posts one Teams message, not one per
 * request.
 *
 * Errors are grouped by status, exception type and route (see [groupOf]). The first occurrence of a group is
 * sent right away ([tryAcquire] returns true); later ones are only counted. [drainRepeats], called once per
 * [window], hands back the counted repeats for a digest and forgets groups that stayed quiet for a whole
 * window, so the next occurrence of those is sent immediately again.
 */
class ExceptionNotificationThrottle(
    private val window: Duration,
    private val maxGroups: Int = DEFAULT_MAX_GROUPS,
    private val now: () -> Instant = Instant::now,
) {
    companion object {
        /** Bounds memory when many distinct routes fail (e.g. a scanner probing random URLs). */
        const val DEFAULT_MAX_GROUPS = 500

        /** Group that collects everything once [maxGroups] distinct groups are being tracked. */
        const val OVERFLOW_GROUP = "(other errors)"

        private val UUID_SEGMENT = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val NUMERIC_SEGMENT = Regex("(?<=/)\\d+(?=/|$)")

        /**
         * The group an error belongs to: status, exception simple name, and the request path with its query
         * string dropped and ids replaced by `{id}`, e.g. `403 ForbiddenException GET /api/studies/{id}/exports`.
         */
        fun groupOf(errorResponse: CarpErrorResponse): String {
            val exception = errorResponse.exception.substringAfterLast('.')
            val route =
                errorResponse.path
                    .substringBefore('?')
                    .replace(UUID_SEGMENT, "{id}")
                    .replace(NUMERIC_SEGMENT, "{id}")
            return "${errorResponse.statusCode} $exception $route"
        }
    }

    private class Group(var lastSentAt: Instant, var repeats: Int = 0)

    private val groups = ConcurrentHashMap<String, Group>()

    /**
     * Records an occurrence of [group]. Returns true when it is the first in its window and should be sent now;
     * false when it was counted towards the next digest instead.
     */
    fun tryAcquire(group: String): Boolean {
        val key = if (groups.size >= maxGroups && !groups.containsKey(group)) OVERFLOW_GROUP else group
        var send = false
        groups.compute(key) { _, existing ->
            if (existing == null) {
                send = true
                Group(now())
            } else {
                existing.apply { repeats++ }
            }
        }
        return send
    }

    /**
     * Returns the repeats counted per group since the last call, resetting them, and forgets groups that had
     * neither a repeat nor a send within the last [window].
     */
    fun drainRepeats(): Map<String, Int> {
        val drainedAt = now()
        val drained = mutableMapOf<String, Int>()
        groups.keys.forEach { key ->
            groups.computeIfPresent(key) { _, group ->
                when {
                    group.repeats > 0 -> {
                        drained[key] = group.repeats
                        group.apply {
                            repeats = 0
                            lastSentAt = drainedAt
                        }
                    }
                    Duration.between(group.lastSentAt, drainedAt) >= window -> null
                    else -> group
                }
            }
        }
        return drained
    }
}
