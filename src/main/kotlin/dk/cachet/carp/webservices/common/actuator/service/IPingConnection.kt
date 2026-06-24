package dk.cachet.carp.webservices.common.actuator.service

import org.springframework.boot.health.contributor.Status

/**
 * The Interface [IPingConnection].
 */
interface IPingConnection {
    /** The [statusHealth] interface. */
    fun statusHealth(): Status
}
