package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.dto.SetAnnouncementRequestDto
import dk.cachet.carp.webservices.admin.repository.AnnouncementStore
import dk.cachet.carp.webservices.admin.service.AnnouncementService
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit

@Service
class AnnouncementServiceImpl(
    private val announcementStore: AnnouncementStore,
    private val authenticationService: AuthenticationService,
    private val clock: Clock = Clock.systemUTC(),
) : AnnouncementService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun get(): Announcement? = withContext(Dispatchers.IO) { announcementStore.find() }

    override suspend fun set(request: SetAnnouncementRequestDto): Announcement? {
        // Read the caller before switching dispatchers: the security context is thread-bound.
        val adminId = authenticationService.getId().stringRepresentation
        val message = request.message?.trim()

        return withContext(Dispatchers.IO) {
            if (message.isNullOrEmpty()) {
                // The row (and its updated_by) is gone afterwards, so the log line is the only audit trail.
                LOGGER.info("System announcement cleared by $adminId.")
                announcementStore.delete()
                null
            } else {
                // Truncate to the column's microsecond precision so the returned value equals what is stored.
                val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                announcementStore.upsert(message, request.severity, now, adminId)
                LOGGER.info("System announcement set by $adminId (${request.severity}).")
                Announcement(message, request.severity, now, adminId)
            }
        }
    }
}
