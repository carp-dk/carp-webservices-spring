package dk.cachet.carp.webservices.admin.dto

import java.time.Instant

data class ServerInfoDto(
    val version: String,
    val environment: String,
    val hostname: String?,
    val startedAt: Instant,
    val uptimeSeconds: Long,
    val jvm: JvmInfoDto,
    val health: HealthInfoDto,
)

data class JvmInfoDto(
    val javaVersion: String,
    val availableProcessors: Int,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
)

/** Overall status plus each dependency's status (e.g. `UP`, `DOWN`), without the underlying details. */
data class HealthInfoDto(
    val status: String,
    val components: Map<String, String>,
)
