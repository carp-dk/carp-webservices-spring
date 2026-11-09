package dk.cachet.carp.webservices.admin.service

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.dto.SetAnnouncementRequestDto

interface AnnouncementService {
    /** The current announcement, or null if none is set. */
    suspend fun get(): Announcement?

    /**
     * Sets or replaces the announcement as the current user, returning it. A null or blank message clears it
     * instead, returning null.
     */
    suspend fun set(request: SetAnnouncementRequestDto): Announcement?
}
