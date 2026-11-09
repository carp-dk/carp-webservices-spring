package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.dto.AdminAnnouncementDto
import dk.cachet.carp.webservices.admin.dto.AdminResearcherDto
import dk.cachet.carp.webservices.admin.dto.AdminStudyDto
import dk.cachet.carp.webservices.admin.dto.ServerInfoDto
import dk.cachet.carp.webservices.admin.dto.SetAnnouncementRequestDto
import dk.cachet.carp.webservices.admin.service.AdminResearcherService
import dk.cachet.carp.webservices.admin.service.AdminStudyService
import dk.cachet.carp.webservices.admin.service.AnnouncementService
import dk.cachet.carp.webservices.admin.service.ServerInfoService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * System-admin endpoints: server status, instance-wide study and researcher overviews, and the system
 * announcement.
 */
@RestController
@RequestMapping(AdminController.ADMIN_BASE)
class AdminController(
    private val serverInfoService: ServerInfoService,
    private val adminStudyService: AdminStudyService,
    private val adminResearcherService: AdminResearcherService,
    private val announcementService: AnnouncementService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        const val ADMIN_BASE = "/api/admin"
        const val SERVER_INFO = "/server-info"
        const val STUDIES = "/studies"
        const val RESEARCHERS = "/researchers"
        const val ANNOUNCEMENT = "/announcement"
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

    @Operation(
        summary = "All researcher accounts on this instance",
        description =
            "Every account with the researcher role, newest first, with its Keycloak details (email, name, " +
                "enabled, email verified, created time) and how many studies (and live studies) it owns.",
    )
    @GetMapping(RESEARCHERS)
    @PreAuthorize("isAdmin()")
    suspend fun getResearchers(): List<AdminResearcherDto> {
        LOGGER.info("Start GET: $ADMIN_BASE$RESEARCHERS")
        return adminResearcherService.getResearchers()
    }

    @Operation(
        summary = "Set or clear the system announcement",
        description =
            "Sets the announcement shown to all users (returns 200 with it), replacing any previous one. " +
                "An empty or missing message clears it instead (returns 204).",
    )
    @PutMapping(ANNOUNCEMENT)
    @PreAuthorize("isAdmin()")
    suspend fun setAnnouncement(
        @Valid @RequestBody request: SetAnnouncementRequestDto,
    ): ResponseEntity<AdminAnnouncementDto> {
        LOGGER.info("Start PUT: $ADMIN_BASE$ANNOUNCEMENT")
        val announcement = announcementService.set(request)
        return if (announcement == null) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(AdminAnnouncementDto.from(announcement))
        }
    }
}
