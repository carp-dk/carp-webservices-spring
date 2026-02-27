@file:Suppress("ImportOrdering")

package dk.cachet.carp.webservices.protocol.service

import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.protocol.domain.Protocol
import dk.cachet.carp.webservices.protocol.repository.ProtocolRepository
import dk.cachet.carp.webservices.protocol.service.impl.ProtocolServiceWrapper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolServiceTest {
    val accountService: AccountService = mockk()
    val protocolRepository: ProtocolRepository = mockk()
    val services: CoreServiceContainer = mockk()
    val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { services.protocolService } returns mockk()
    }

    @Nested
    inner class GetSingleProtocolOverview {
        @Test
        fun `should return null if there are no versions for the id`() =
            runTest {
                every { protocolRepository.findAllByIdSortByCreatedAt(any()) } returns emptyList()

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, services)

                assertNull(sut.getSingleProtocolOverview("id"))
            }
    }

    @Nested
    inner class ResolveVersionTag {
        @Test
        fun `should return matching version tag when snapshot matches`() =
            runTest {
                val protocolId = UUID.randomUUID()
                val snapshot =
                    StudyProtocolSnapshot(
                        id = protocolId,
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "Protocol",
                        applicationData = """{"k":"v"}""",
                    )
                val version =
                    Protocol().apply {
                        versionTag = "v1"
                        this.snapshot =
                            objectMapper.readTree(
                                WS_JSON.encodeToString(StudyProtocolSnapshot.serializer(), snapshot),
                            )
                    }
                every { protocolRepository.findByParams(protocolId.stringRepresentation, null) } returns listOf(version)

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, services)

                assertEquals("v1", sut.resolveVersionTag(snapshot))
            }

        @Test
        fun `should append mismatch suffix when snapshot does not match any stored snapshot`() =
            runTest {
                val protocolId = UUID.randomUUID()
                val requestSnapshot =
                    StudyProtocolSnapshot(
                        id = protocolId,
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "Protocol",
                        applicationData = """{"request":"value"}""",
                    )
                val storedSnapshot =
                    requestSnapshot.copy(
                        applicationData = """{"stored":"value"}""",
                    )
                val latestVersion =
                    Protocol().apply {
                        versionTag = "v2"
                        this.snapshot =
                            objectMapper.readTree(
                                WS_JSON.encodeToString(StudyProtocolSnapshot.serializer(), storedSnapshot),
                            )
                    }
                every { protocolRepository.findByParams(protocolId.stringRepresentation, null) } returns
                    listOf(latestVersion)

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, services)

                assertEquals("v2_snapshot_mismatch", sut.resolveVersionTag(requestSnapshot))
            }
    }
}
