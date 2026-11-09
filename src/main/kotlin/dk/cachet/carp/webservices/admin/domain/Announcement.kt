package dk.cachet.carp.webservices.admin.domain

import java.time.Instant

/** The single system-wide announcement, as stored in `system_announcement`. */
data class Announcement(
    val message: String,
    val severity: AnnouncementSeverity,
    val updatedAt: Instant,
    /** Account id of the admin who last set it. */
    val updatedBy: String,
)
