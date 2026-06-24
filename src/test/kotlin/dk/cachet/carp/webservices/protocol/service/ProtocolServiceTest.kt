@file:Suppress("ImportOrdering")

package dk.cachet.carp.webservices.protocol.service

import tools.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.ProtocolService as CoreProtocolService
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceDecorator
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceRequest
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.protocol.repository.ProtocolRepository
import dk.cachet.carp.webservices.protocol.service.impl.ProtocolServiceWrapper
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    val coreProtocolService: CoreProtocolService = mockk()
    val core: ProtocolServiceDecorator = ProtocolServiceDecorator(coreProtocolService) { request -> request }
    val objectMapper = ObjectMapper()
    val applicationDataService = ApplicationDataService(objectMapper)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { services.protocolService } returns core
    }

    @Nested
    inner class GetSingleProtocolOverview {
        @Test
        fun `should return null if there are no versions for the id`() =
            runTest {
                every { protocolRepository.findAllByIdSortByCreatedAt(any()) } returns emptyList()

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, applicationDataService, services)

                assertNull(sut.getSingleProtocolOverview("id"))
            }
    }

    @Nested
    inner class Invoke {
        @Test
        fun `should preprocess Add by adding protocolVersionTag into protocol applicationData`() =
            runTest {
                val protocol =
                    StudyProtocolSnapshot(
                        id = UUID.randomUUID(),
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "Protocol",
                        applicationData = """{"applicationName":"My App"}""",
                    )
                val request = ProtocolServiceRequest.Add(protocol, "v1.2.3")
                val updatedProtocolSlot = slot<StudyProtocolSnapshot>()
                val result = Unit

                coEvery { coreProtocolService.add(capture(updatedProtocolSlot), "v1.2.3") } returns result

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, applicationDataService, services)

                assertEquals(result, sut.invoke(request))
                val applicationDataNode = objectMapper.readTree(updatedProtocolSlot.captured.applicationData)
                assertEquals("My App", applicationDataNode.path("applicationName").asText())
                assertEquals("v1.2.3", applicationDataNode.path("protocolVersionTag").asText())
            }

        @Test
        fun `should preprocess AddVersion legacy applicationData by wrapping as legacyApplicationData`() =
            runTest {
                val protocol =
                    StudyProtocolSnapshot(
                        id = UUID.randomUUID(),
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "Protocol",
                        applicationData = "legacy-string",
                    )
                val request = ProtocolServiceRequest.AddVersion(protocol, "v9")
                val updatedProtocolSlot = slot<StudyProtocolSnapshot>()
                val result = Unit

                coEvery { coreProtocolService.addVersion(capture(updatedProtocolSlot), "v9") } returns result

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, applicationDataService, services)

                assertEquals(result, sut.invoke(request))
                val applicationDataNode = objectMapper.readTree(updatedProtocolSlot.captured.applicationData)
                assertEquals("v9", applicationDataNode.path("protocolVersionTag").asText())
                assertEquals("legacy-string", applicationDataNode.path("legacyApplicationData").asText())
            }

        @Test
        fun `should delegate request handling to core service`() =
            runTest {
                val ownerId = UUID.randomUUID()
                val request = ProtocolServiceRequest.GetAllForOwner(ownerId)
                val result = emptyList<StudyProtocolSnapshot>()

                coEvery { coreProtocolService.getAllForOwner(ownerId) } returns result

                val sut = ProtocolServiceWrapper(accountService, protocolRepository, applicationDataService, services)

                assertEquals(result, sut.invoke(request))
                coVerify(exactly = 1) { coreProtocolService.getAllForOwner(ownerId) }
            }
    }
}
