package dk.cachet.carp.webservices.common.actuator.service

import org.springframework.boot.health.contributor.Status

/**
 * The Interface [IDatabaseConnection].
 */
interface IDatabaseConnection {
    /** The [statusHealth] interface. */
    fun statusHealth(): Status

    /** The [statusDetails] interface. */
    fun statusDetails(): MutableMap<String, Any>?
}
