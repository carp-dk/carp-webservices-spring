package dk.cachet.carp.webservices.admin.controller

import dk.cachet.carp.webservices.admin.dto.AnnouncementDto
import dk.cachet.carp.webservices.admin.service.AnnouncementService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * Public, unauthenticated read of the system announcement (permit-all in application.yml), so clients can
 * show it before sign-in, e.g. a maintenance notice on the login page.
 */
@RestController
class AnnouncementController(
    private val announcementService: AnnouncementService,
) {
    companion object {
        const val ANNOUNCEMENT = "/api/announcement"

        // Every client polls this; a short shared cache keeps it off the database without making a newly set
        // announcement noticeably late.
        private val CACHE_CONTROL = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()
    }

    @Operation(
        summary = "Current system announcement",
        description = "The announcement to show to all users (200), or 204 if none is set. No authentication.",
    )
    @GetMapping(ANNOUNCEMENT)
    suspend fun getAnnouncement(): ResponseEntity<AnnouncementDto> {
        val announcement =
            announcementService.get()
                ?: return ResponseEntity.noContent().cacheControl(CACHE_CONTROL).build()
        return ResponseEntity.ok().cacheControl(CACHE_CONTROL).body(AnnouncementDto.from(announcement))
    }
}
