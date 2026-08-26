package dk.cachet.carp.webservices.selfsignup.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/**
 * Basic per-IP rate limiting for the public self-signup endpoint, backed by Postgres rather than an
 * in-memory counter or Redis: the app already sits behind a reverse proxy (see
 * `server.forward-headers-strategy` / `ForwardedHeaderFilter`), so an in-memory counter isn't safe against
 * a future multi-instance deployment, and this codebase already favors Postgres over new infra for this
 * class of problem. Uses fixed 1-minute windows (one row per IP per window) rather than a true sliding
 * window - simpler, and the known boundary-burst weakness (up to 2x the nominal limit across a window
 * edge) is an accepted trade-off for basic abuse prevention on a single endpoint.
 */
@Component
class SelfSignupRateLimitStore(
    private val jdbcTemplate: JdbcTemplate,
) {
    /** Increments (or creates) [ip]'s bucket for [windowStart] and returns the resulting count. */
    fun incrementAndGet(
        ip: String,
        windowStart: Instant,
    ): Int =
        checkNotNull(
            jdbcTemplate.queryForObject(
                """
                INSERT INTO self_signup_rate_limit (ip_address, window_start, request_count)
                VALUES (?, ?, 1)
                ON CONFLICT (ip_address, window_start)
                DO UPDATE SET request_count = self_signup_rate_limit.request_count + 1
                RETURNING request_count
                """.trimIndent(),
                Int::class.java,
                ip,
                Timestamp.from(windowStart),
            ),
        )

    /** Deletes windows older than [cutoff]; returns the number of rows removed. Bounds table growth. */
    fun deleteOlderThan(cutoff: Instant): Int =
        jdbcTemplate.update(
            "DELETE FROM self_signup_rate_limit WHERE window_start < ?",
            Timestamp.from(cutoff),
        )
}
