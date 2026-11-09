package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.dto.AdminStudyDto
import dk.cachet.carp.webservices.admin.dto.ServerInfoDto
import dk.cachet.carp.webservices.admin.service.AdminStudyService
import dk.cachet.carp.webservices.admin.service.ServerInfoService
import io.swagger.v3.oas.annotations.Operation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** System-admin endpoints: server status and an instance-wide study overview. */
@RestController
@RequestMapping(AdminController.ADMIN_BASE)
class AdminController(
    private val serverInfoService: ServerInfoService,
    private val adminStudyService: AdminStudyService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        const val ADMIN_BASE = "/api/admin"
        const val SERVER_INFO = "/server-info"
        const val STUDIES = "/studies"
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

    @Operation(
        summary = "All studies on this instance",
        description =
            "Every study, newest first, with its owner, created/updated time, first deployment time and " +
                "participant/deployment/file counts. Pass `live` to list only live (true) or only non-live " +
                "(false) studies. Uploaded data is per study, from `GET /api/studies/{study-id}/data-uploads`.",
    )
    @GetMapping(STUDIES)
    @PreAuthorize("isAdmin()")
    suspend fun getStudies(
        @RequestParam(required = false) live: Boolean?,
    ): List<AdminStudyDto> {
        LOGGER.info("Start GET: $ADMIN_BASE$STUDIES")
        return adminStudyService.getStudies(live)
    }
}
