package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.dto.ServerInfoDto
import dk.cachet.carp.webservices.admin.service.ServerInfoService
import io.swagger.v3.oas.annotations.Operation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** System-admin endpoints: server status. */
@RestController
@RequestMapping(AdminController.ADMIN_BASE)
class AdminController(
    private val serverInfoService: ServerInfoService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        const val ADMIN_BASE = "/api/admin"
        const val SERVER_INFO = "/server-info"
    }

    @Operation(
        summary = "Server version, runtime and dependency health",
        description =
            "Application version, environment, host, uptime, JVM heap, and the status (UP/DOWN/...) of each " +
                "dependency: database, RabbitMQ, mail, disk space and Keycloak.",
    )
    @GetMapping(SERVER_INFO)
    @PreAuthorize("isAdmin()")
    suspend fun getServerInfo(): ServerInfoDto {
        LOGGER.info("Start GET: $ADMIN_BASE$SERVER_INFO")
        return serverInfoService.getServerInfo()
    }
}
