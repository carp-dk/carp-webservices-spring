package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.webservices.common.environment.EnvironmentProfile
import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.actuate.endpoint.StatusAggregator
import org.springframework.boot.health.contributor.Status
import java.lang.management.RuntimeMXBean
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerInfoServiceImplTest {
    private val startedAt = Instant.parse("2026-11-09T08:00:00Z")
    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val environmentUtil = mockk<EnvironmentUtil> { every { profile } returns EnvironmentProfile.PRODUCTION }
    private val issuerFacade = mockk<IssuerFacade>()
    private val healthEndpoint = mockk<HealthEndpoint>()
    private val runtimeMXBean = mockk<RuntimeMXBean> { every { startTime } returns startedAt.toEpochMilli() }

    private val sut =
        ServerInfoServiceImpl(
            version = "2.7.0",
            environmentUtil = environmentUtil,
            healthEndpoint = healthEndpoint,
            issuerFacade = issuerFacade,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            runtimeMXBean = runtimeMXBean,
        )

    private fun component(status: Status) = mockk<HealthDescriptor> { every { this@mockk.status } returns status }

    private fun actuatorReports(vararg components: Pair<String, Status>) {
        val composite =
            mockk<CompositeHealthDescriptor> {
                every { this@mockk.components } returns
                    components.associate { (name, status) -> name to component(status) }
                // As the actuator does: its overall status is the severity-ordered aggregate of the components.
                every { status } returns
                    StatusAggregator.getDefault().getAggregateStatus(components.map { it.second }.toSet())
            }
        every { healthEndpoint.health() } returns composite
    }

    @Test
    fun `reports version, runtime and every component UP`() =
        runTest {
            actuatorReports("db" to Status.UP, "rabbit" to Status.UP)
            coEvery { issuerFacade.isReachable() } returns true

            val info = sut.getServerInfo()

            assertEquals("2.7.0", info.version)
            assertEquals("production", info.environment)
            assertEquals(startedAt, info.startedAt)
            assertEquals(7200, info.uptimeSeconds)
            assertEquals("UP", info.health.status)
            assertEquals(mapOf("db" to "UP", "rabbit" to "UP", "keycloak" to "UP"), info.health.components)
        }

    @Test
    fun `an unreachable Keycloak is reported DOWN, and so is the overall status`() =
        runTest {
            actuatorReports("db" to Status.UP)
            coEvery { issuerFacade.isReachable() } returns false

            val info = sut.getServerInfo()

            assertEquals("DOWN", info.health.components["keycloak"])
            assertEquals("DOWN", info.health.status)
        }

    @Test
    fun `a down actuator component makes the overall status DOWN`() =
        runTest {
            actuatorReports("db" to Status.UP, "rabbit" to Status.DOWN)
            coEvery { issuerFacade.isReachable() } returns true

            assertEquals("DOWN", sut.getServerInfo().health.status)
        }

    @Test
    fun `the overall status is the most severe one, not the first non-UP one`() =
        runTest {
            // OUT_OF_SERVICE is listed first, but a DOWN Keycloak outranks it.
            actuatorReports("readinessState" to Status.OUT_OF_SERVICE, "db" to Status.UP)
            coEvery { issuerFacade.isReachable() } returns false

            assertEquals("DOWN", sut.getServerInfo().health.status)
        }

    @Test
    fun `UNKNOWN components do not degrade the overall status`() =
        runTest {
            actuatorReports("db" to Status.UP, "custom" to Status.UNKNOWN)
            coEvery { issuerFacade.isReachable() } returns true

            assertEquals("UP", sut.getServerInfo().health.status)
        }
}
