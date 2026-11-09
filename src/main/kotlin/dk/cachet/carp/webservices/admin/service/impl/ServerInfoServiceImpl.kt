package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.webservices.admin.dto.HealthInfoDto
import dk.cachet.carp.webservices.admin.dto.JvmInfoDto
import dk.cachet.carp.webservices.admin.dto.ServerInfoDto
import dk.cachet.carp.webservices.admin.service.ServerInfoService
import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.actuate.endpoint.StatusAggregator
import org.springframework.boot.health.contributor.Status
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
@Suppress("LongParameterList") // clock and runtimeMXBean are only overridden by tests
class ServerInfoServiceImpl(
    @param:Value("\${spring.application.version}") private val version: String,
    private val environmentUtil: EnvironmentUtil,
    private val healthEndpoint: HealthEndpoint,
    private val issuerFacade: IssuerFacade,
    private val statusAggregator: StatusAggregator = StatusAggregator.getDefault(),
    private val clock: Clock = Clock.systemUTC(),
    private val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean(),
) : ServerInfoService {
    companion object {
        const val KEYCLOAK_COMPONENT = "keycloak"
    }

    override suspend fun getServerInfo(): ServerInfoDto =
        coroutineScope {
            val actuatorHealth = async(Dispatchers.IO) { healthEndpoint.health() }
            val keycloakReachable = async { issuerFacade.isReachable() }

            val health = actuatorHealth.await()
            val keycloakStatus = if (keycloakReachable.await()) Status.UP else Status.DOWN
            val components = componentStatuses(health) + (KEYCLOAK_COMPONENT to keycloakStatus.code)
            // The actuator's own aggregate already orders its components by severity (and honours any configured
            // status order); fold Keycloak in with the same aggregator.
            val overall = statusAggregator.getAggregateStatus(health.status, keycloakStatus)

            val startedAt = Instant.ofEpochMilli(runtimeMXBean.startTime)
            val runtime = Runtime.getRuntime()

            ServerInfoDto(
                version = version,
                environment = environmentUtil.profile.value,
                hostname = runCatching { InetAddress.getLocalHost().hostName }.getOrNull(),
                startedAt = startedAt,
                uptimeSeconds = Duration.between(startedAt, clock.instant()).seconds,
                jvm =
                    JvmInfoDto(
                        javaVersion = Runtime.version().toString(),
                        availableProcessors = runtime.availableProcessors(),
                        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                        heapMaxBytes = runtime.maxMemory(),
                    ),
                health = HealthInfoDto(status = overall.code, components = components),
            )
        }

    /** Each top-level actuator health contributor (db, rabbit, diskSpace, ...) mapped to its status code. */
    private fun componentStatuses(health: HealthDescriptor): Map<String, String> =
        if (health is CompositeHealthDescriptor) {
            health.components.orEmpty().mapValues { (_, component) -> component.status.code }
        } else {
            mapOf("application" to health.status.code)
        }
}
