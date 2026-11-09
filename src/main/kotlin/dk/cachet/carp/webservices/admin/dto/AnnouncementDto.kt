package dk.cachet.carp.webservices.admin.dto

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.domain.AnnouncementSeverity
import jakarta.validation.constraints.Size
import java.time.Instant

/** Body of `PUT /api/admin/announcement`. A null or blank [message] clears the announcement. */
data class SetAnnouncementRequestDto(
    @field:Size(max = SetAnnouncementRequestDto.MAX_MESSAGE_LENGTH)
    val message: String? = null,
    val severity: AnnouncementSeverity = AnnouncementSeverity.INFO,
) {
    companion object {
        const val MAX_MESSAGE_LENGTH = 1000
    }
}

/** The announcement as shown to everyone, via the public `GET /api/announcement`. */
data class AnnouncementDto(
    val message: String,
    val severity: AnnouncementSeverity,
    val updatedAt: Instant,
) {
    companion object {
        fun from(announcement: Announcement) =
            AnnouncementDto(announcement.message, announcement.severity, announcement.updatedAt)
    }
}

/** The announcement as returned to the admin who set it; additionally names who last set it. */
data class AdminAnnouncementDto(
    val message: String,
    val severity: AnnouncementSeverity,
    val updatedAt: Instant,
    val updatedBy: String,
) {
    companion object {
        fun from(announcement: Announcement) =
            AdminAnnouncementDto(
                announcement.message,
                announcement.severity,
                announcement.updatedAt,
                announcement.updatedBy,
            )
    }
}
