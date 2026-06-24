package dk.cachet.carp.webservices.protocol.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.ProtocolVersion
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.protocols.domain.StudyProtocol
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.protocol.domain.Protocol
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreProtocolRepositoryTest {
    private val protocolRepository = mockk<ProtocolRepository>()
    private val validationMessages = mockk<MessageBase>()
    private val objectMapper = mockk<ObjectMapper>()

    @Nested
    inner class Add {
        @Test
        fun `should throw if protocol already exists`() {
            runTest {
                val protocol = mockk<StudyProtocol>(relaxed = true)

                coEvery { protocolRepository.findAllById(any<String>()) } returns listOf(mockk())
                coEvery { validationMessages.get("protocol.id.already.exists", protocol.id) } returns "error"

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<IllegalStateException> {
                    sut.add(protocol, mockk())
                }

                verify { validationMessages.get("protocol.id.already.exists", protocol.id) }
            }
        }

        @Test
        fun `should throw if protocol stored with given params already exists`() {
            runTest {
                val protocol = mockk<StudyProtocol>(relaxed = true)
                val version = mockk<ProtocolVersion>(relaxed = true)

                coEvery { protocolRepository.findAllById(any<String>()) } returns listOf()
                coEvery { protocolRepository.findByParams(any(), any()) } returns listOf(mockk())
                coEvery { validationMessages.get("protocol.already.exists", any(), any(), any()) } returns "error"

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<IllegalStateException> {
                    sut.add(protocol, version)
                }

                verify { validationMessages.get("protocol.already.exists", any(), any(), any()) }
            }
        }

        @Test
        fun `should save the protocol`() {
            runTest {
                val protocol = mockk<StudyProtocol>()
                val version = mockk<ProtocolVersion>(relaxed = true)
                coEvery { protocol.getSnapshot() } returns
                    StudyProtocolSnapshot(
                        id = UUID.randomUUID(),
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "1",
                    )
                coEvery { protocol.id } returns UUID.randomUUID()
                coEvery { protocol.name } returns "1"

                coEvery { protocolRepository.findAllById(any<String>()) } returns listOf()
                coEvery { protocolRepository.findByParams(any(), any()) } returns listOf()
                coEvery { protocolRepository.save(any()) } returns mockk()
                coEvery { objectMapper.readTree(any<String>()) } returns mockk()

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                sut.add(protocol, version)

                coVerify { protocolRepository.save(any<Protocol>()) }
            }
        }
    }

    @Nested
    inner class GetAllForOwner {
        @Test
        fun `should return a sequence of protocols`() {
            runTest {
                val studyProtocolSnapshot =
                    StudyProtocolSnapshot(
                        id = UUID.randomUUID(),
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "1",
                    )
                val snapshot = WS_JSON.encodeToString(StudyProtocolSnapshot.serializer(), studyProtocolSnapshot)
                val jsonNode = ObjectMapper().readTree(snapshot)
                val protocol1 = mockk<Protocol>()
                coEvery { protocol1.snapshot } returns jsonNode

                coEvery { protocolRepository.findAllByOwnerId(any()) } returns listOf(protocol1)

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                val protocols = sut.getAllForOwner(studyProtocolSnapshot.ownerId)

                assertEquals(1, protocols.count())
            }
        }

        @Test
        fun `should throw if snapshot of a protocol is null`() {
            runTest {
                val protocol = mockk<Protocol>()
                coEvery { protocol.snapshot } returns null

                coEvery { protocolRepository.findAllByOwnerId(any()) } returns listOf(protocol)

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<NullPointerException> {
                    sut.getAllForOwner(UUID.randomUUID())
                }
            }
        }
    }

    @Nested
    inner class GetBy {
        @Test
        fun `should return the protocol`() {
            runTest {
                val studyProtocolSnapshot =
                    StudyProtocolSnapshot(
                        id = UUID.randomUUID(),
                        createdOn = Instant.fromEpochMilliseconds(0),
                        version = 1,
                        ownerId = UUID.randomUUID(),
                        name = "1",
                    )
                val snapshot = WS_JSON.encodeToString(StudyProtocolSnapshot.serializer(), studyProtocolSnapshot)
                val jsonNode = ObjectMapper().readTree(snapshot)
                val protocol1 = mockk<Protocol>()
                coEvery { protocol1.snapshot } returns jsonNode

                coEvery { protocolRepository.findByParams(any(), any()) } returns listOf(protocol1)

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                sut.getBy(studyProtocolSnapshot.ownerId, "1")

                assertTrue { true }
            }
        }

        @Test
        fun `should throw if the snapshot of the protocol is missing`() {
            runTest {
                val protocol = mockk<Protocol>()
                coEvery { protocol.snapshot } returns null

                coEvery { protocolRepository.findByParams(any(), any()) } returns listOf(protocol)

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<NullPointerException> {
                    sut.getBy(UUID.randomUUID(), "1")
                }
            }
        }

        @Test
        fun `should return null if the protocol was not found`() {
            runTest {
                coEvery { protocolRepository.findByParams(any(), any()) } returns emptyList()

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                val result = sut.getBy(UUID.randomUUID(), "1")

                assertEquals(null, result)
            }
        }
    }

    @Nested
    inner class GetVersionHistoryFor {
        @Test
        fun `should retrieve version history`() {
            runTest {
                val protocol1 = mockk<Protocol>(relaxed = true)
                val protocol2 = mockk<Protocol>(relaxed = true)
                coEvery { protocolRepository.findByParams(any(), any()) } returns listOf(protocol1, protocol2)
                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                val result = sut.getVersionHistoryFor(UUID.randomUUID())

                assertEquals(2, result.size)
            }
        }

        @Test
        fun `should throw if no protocols are found`() {
            runTest {
                coEvery { protocolRepository.findByParams(any(), any()) } returns emptyList()
                coEvery { validationMessages.get(any<String>(), any()) } returns ""

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<IllegalStateException> {
                    sut.getVersionHistoryFor(UUID.randomUUID())
                }
            }
        }
    }

    @Nested
    inner class Replace {
        @Test
        fun `should throw if protocol not found`() {
            runTest {
                val protocol = mockk<StudyProtocol>(relaxed = true)
                coEvery { protocolRepository.findByParams(any(), any()) } returns emptyList()
                coEvery { validationMessages.get(any<String>(), any(), any()) } returns ""

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<IllegalStateException> {
                    sut.replace(protocol, ProtocolVersion("1", Instant.fromEpochMilliseconds(0)))
                }
            }
        }

        @Test
        fun `should replace with new version`() {
            runTest {
                val version = mockk<ProtocolVersion>(relaxed = true)
                val newProtocol =
                    StudyProtocol(
                        id = UUID.randomUUID(),
                        ownerId = UUID.randomUUID(),
                        name = "Test Protocol",
                        createdOn = Instant.fromEpochMilliseconds(0),
                    )
                val existingProtocol = mockk<Protocol>(relaxed = true)
                coEvery {
                    protocolRepository.findByParams(
                        newProtocol.id.stringRepresentation, version.tag,
                    )
                } returns listOf(existingProtocol)
                coEvery { protocolRepository.delete(any<Protocol>()) } returns Unit
                coEvery { protocolRepository.save(any<Protocol>()) } returns mockk()
                coEvery { objectMapper.readTree(any<String>()) } returns mockk()
                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                sut.replace(newProtocol, version)

                coVerify { protocolRepository.delete(existingProtocol) }
                coVerify { protocolRepository.save(any<Protocol>()) }
            }
        }
    }

    @Nested
    inner class AddVersion {
        @Test
        fun `should throw if the protocol not found`() {
            runTest {
                val protocol = mockk<StudyProtocol>(relaxed = true)
                coEvery { protocolRepository.findAllById(any<String>()) } returns emptyList()
                coEvery { validationMessages.get(any<String>(), any(), any()) } returns ""

                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<IllegalArgumentException> {
                    sut.addVersion(protocol, ProtocolVersion("1", Instant.fromEpochMilliseconds(0)))
                }
            }
        }

        @Test
        fun `should throw if protocol with this version already exists`() {
            runTest {
                val studyProtocol =
                    mockk<StudyProtocol> {
                        every { id } returns UUID.randomUUID()
                        every { name } returns "Test Protocol"
                        every { ownerId } returns UUID.randomUUID()
                    }
                val returnedProtocol =
                    mockk<Protocol> {
                        every { versionTag } returns "1"
                    }
                val version =
                    mockk<ProtocolVersion> {
                        every { tag } returns "1"
                    }
                coEvery { protocolRepository.findAllById(any<String>()) } returns listOf(returnedProtocol)
                coEvery { validationMessages.get(any(), any(), any(), any()) } returns ""
                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                assertThrows<IllegalArgumentException> {
                    sut.addVersion(studyProtocol, version)
                }
            }
        }

        @Test
        fun `should add a new version`() {
            runTest {
                val protocol =
                    StudyProtocol(
                        id = UUID.randomUUID(),
                        ownerId = UUID.randomUUID(),
                        name = "Test Protocol",
                        createdOn = Instant.fromEpochMilliseconds(0),
                    )
                val returnedProtocol =
                    mockk<Protocol> {
                        every { versionTag } returns "1"
                    }
                val version =
                    mockk<ProtocolVersion> {
                        every { tag } returns "2"
                    }
                coEvery { protocolRepository.findAllById(any<String>()) } returns listOf(returnedProtocol)
                coEvery { objectMapper.readTree(any<String>()) } returns mockk()
                coEvery { protocolRepository.save(any()) } returns mockk()
                val sut =
                    CoreProtocolRepository(
                        protocolRepository,
                        validationMessages,
                        objectMapper,
                    )

                sut.addVersion(protocol, version)

                coVerify { protocolRepository.save(any<Protocol>()) }
            }
        }
    }
}
