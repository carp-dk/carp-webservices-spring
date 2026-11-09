package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.domain.AnnouncementSeverity
import dk.cachet.carp.webservices.admin.dto.SetAnnouncementRequestDto
import dk.cachet.carp.webservices.admin.repository.AnnouncementStore
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnnouncementServiceImplTest {
    private val store = mockk<AnnouncementStore>()
    private val adminId = UUID.randomUUID()
    private val authenticationService = mockk<AuthenticationService> { every { getId() } returns adminId }
    private val now = Instant.parse("2026-11-09T10:00:00.123456789Z")
    private val sut = AnnouncementServiceImpl(store, authenticationService, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `a non-blank message is trimmed and upserted as the current admin`() =
        runTest {
            justRun { store.upsert(any(), any(), any(), any()) }
            val storedAt = Instant.parse("2026-11-09T10:00:00.123456Z") // truncated to the column's precision

            val request = SetAnnouncementRequestDto("  Maintenance tonight.  ", AnnouncementSeverity.WARNING)
            val result = sut.set(request)

            val admin = adminId.stringRepresentation
            assertEquals(Announcement("Maintenance tonight.", AnnouncementSeverity.WARNING, storedAt, admin), result)
            verify(exactly = 1) { store.upsert("Maintenance tonight.", AnnouncementSeverity.WARNING, storedAt, admin) }
            verify(exactly = 0) { store.delete() }
        }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   ", "\n\t"])
    fun `a null or blank message clears the announcement`(message: String?) =
        runTest {
            justRun { store.delete() }

            val result = sut.set(SetAnnouncementRequestDto(message, AnnouncementSeverity.CRITICAL))

            assertNull(result)
            verify(exactly = 1) { store.delete() }
            verify(exactly = 0) { store.upsert(any(), any(), any(), any()) }
        }
}
