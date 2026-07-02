package dk.cachet.carp.webservices.datastream.service.core

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.data.DataType
import dk.cachet.carp.common.infrastructure.test.StubDataPoint
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.data.application.DataStreamsConfiguration
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.domain.DataStreamConfiguration
import dk.cachet.carp.webservices.datastream.repository.DataStreamConfigurationRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamIdRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.datastream.service.createStubSequence
import dk.cachet.carp.webservices.datastream.service.impl.MutableDataStreamBatchDecorator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoreDataStreamServiceTest {
    private lateinit var sut: CoreDataStreamService
    private val configRepository = mockk<DataStreamConfigurationRepository>()
    private val dataStreamIdRepository = mockk<DataStreamIdRepository>()
    private val dataStreamSequenceRepository = mockk<DataStreamSequenceRepository>()
    private val objectMapper = mockk<ObjectMapper>()

    @BeforeEach
    fun setUp() {
        sut =
            CoreDataStreamService(
                configRepository,
                dataStreamIdRepository,
                dataStreamSequenceRepository,
                objectMapper,
            )
    }

    @Nested
    inner class AppendToDataStreams {
        @Test
        fun `throw if there are incorrect study deployment IDs in any of the batches`() =
            runTest {
                val incorrectId = UUID.randomUUID()
                val correctId = UUID.randomUUID()
                val batch = MutableDataStreamBatchDecorator()
                batch.appendSequence(createStubSequence(correctId, 0, StubDataPoint()))
                batch.appendSequence(createStubSequence(incorrectId, 0, StubDataPoint()))

                assertThrows<IllegalArgumentException> {
                    sut.appendToDataStreams(correctId, batch)
                }
            }

        @Test
        fun `throw if study deployment ID is not in configuration`() =
            runTest {
                val dataStreamConfigurationRepository = mockk<DataStreamConfigurationRepository>()
                val studyDeploymentId = UUID.randomUUID()

                val batch = MutableDataStreamBatchDecorator()
                batch.appendSequence(createStubSequence(studyDeploymentId, 0, StubDataPoint()))

                coEvery {
                    dataStreamConfigurationRepository.findById(studyDeploymentId.stringRepresentation)
                } returns Optional.empty()

                val sut =
                    CoreDataStreamService(
                        dataStreamConfigurationRepository,
                        mockk(),
                        mockk(),
                        mockk(),
                    )

                assertFailsWith<IllegalArgumentException> {
                    sut.appendToDataStreams(studyDeploymentId, batch)
                }

                coVerify(exactly = 1) {
                    dataStreamConfigurationRepository.findById(studyDeploymentId.stringRepresentation)
                }
            }
    }

    @Nested
    inner class GetDataStreams {
        @Test
        fun `throw IllegalArgumentException when fromSequenceId is negative`() =
            runTest {
                val dataStreamId =
                    DataStreamId(
                        UUID.randomUUID(),
                        "deviceRole",
                        DataType("namespace", "name"),
                    )

                assertThrows<IllegalArgumentException> {
                    sut.getDataStream(dataStreamId, -1, null)
                }
            }

        @Test
        fun `throw IllegalArgumentException when toSequenceIdInclusive is smaller than fromSequenceId`() =
            runTest {
                val dataStreamId =
                    DataStreamId(
                        UUID.randomUUID(),
                        "deviceRole",
                        DataType("namespace", "name"),
                    )

                assertThrows<IllegalArgumentException> {
                    sut.getDataStream(dataStreamId, 10, 5)
                }
            }

        @Test
        fun `throw IllegalArgumentException when no configuration is found for the given studyDeploymentId`() =
            runTest {
                val dataStreamId =
                    DataStreamId(
                        UUID.randomUUID(),
                        "deviceRole",
                        DataType("namespace", "name"),
                    )
                coEvery {
                    configRepository.findById(dataStreamId.studyDeploymentId.stringRepresentation)
                } returns Optional.empty()

                assertThrows<IllegalArgumentException> {
                    sut.getDataStream(dataStreamId, 0, null)
                }
            }

        @Test
        fun `throw IllegalArgumentException when dataStream is not configured for the given studyDeploymentId`() =
            runTest {
                val dataStreamId =
                    DataStreamId(
                        UUID.randomUUID(),
                        "deviceRole",
                        DataType("namespace", "name"),
                    )
                val config = mockk<DataStreamConfiguration>()
                val configNode = mockk<JsonNode>()
                coEvery {
                    configRepository.findById(dataStreamId.studyDeploymentId.stringRepresentation)
                } returns Optional.of(config)
                coEvery { config.config } returns configNode
                val coreConfig = mockk<DataStreamsConfiguration>()
                coEvery {
                    objectMapper.treeToValue(configNode, DataStreamsConfiguration::class.java)
                } returns coreConfig
                coEvery { coreConfig.expectedDataStreamIds } returns emptySet()

                assertThrows<IllegalArgumentException> {
                    sut.getDataStream(dataStreamId, 0, null)
                }
            }
    }

    @Nested
    inner class GetDataStreamsStatus {
        @Test
        fun `loads stream IDs and maximum sequence IDs in batches`() =
            runTest {
                val deploymentId = UUID.randomUUID()
                val expectedStream = DataStreamId(deploymentId, "phone", DataType("dk.cachet", "steps"))
                val configuration =
                    DataStreamsConfiguration(
                        deploymentId,
                        setOf(DataStreamsConfiguration.ExpectedDataStream("phone", DataType("dk.cachet", "steps"))),
                    )
                val storedStream =
                    dk.cachet.carp.webservices.datastream.domain.DataStreamId(
                        id = 42,
                        studyDeploymentId = deploymentId.stringRepresentation,
                        deviceRoleName = "phone",
                        name = "steps",
                        nameSpace = "dk.cachet",
                    )
                val maximum = mockk<DataStreamSequenceRepository.MaximumSequenceId>()
                every { maximum.dataStreamId } returns 42
                every { maximum.lastSequenceId } returns 99
                every { configRepository.findById(deploymentId.stringRepresentation) } returns
                    Optional.of(
                        DataStreamConfiguration(
                            deploymentId.stringRepresentation,
                            ObjectMapper().readTree(
                                WS_JSON.encodeToString(DataStreamsConfiguration.serializer(), configuration),
                            ),
                        ),
                    )
                every { dataStreamIdRepository.getAllByDeploymentId(deploymentId.stringRepresentation) } returns
                    listOf(storedStream)
                every { dataStreamSequenceRepository.findMaxLastSequenceIdsByDataStreamIds(listOf(42)) } returns
                    listOf(maximum)

                val result = sut.getDataStreamsStatus(deploymentId)

                assertEquals(expectedStream, result.single().dataStream)
                assertEquals(99, result.single().lastSequenceId)
                coVerify(exactly = 1) { dataStreamIdRepository.getAllByDeploymentId(any()) }
                coVerify(exactly = 1) { dataStreamSequenceRepository.findMaxLastSequenceIdsByDataStreamIds(any()) }
            }
    }

    @Nested
    inner class OpenDataStreams {
//  TODO: Fix unit tests
//        @Test
        fun `save configuration and data stream IDs when valid configuration is provided`() =
            runTest {
                val studyDeploymentId = UUID.randomUUID()
                val configuration =
                    DataStreamsConfiguration(
                        studyDeploymentId,
                        setOf(
                            DataStreamsConfiguration.ExpectedDataStream(
                                "deviceRole",
                                DataType("namespace", "name"),
                            ),
                        ),
                    )

                val wsDataStreamId =
                    dk.cachet.carp.webservices.datastream.domain.DataStreamId(
                        id = 0,
                        studyDeploymentId = studyDeploymentId.stringRepresentation,
                        deviceRoleName = "deviceRole",
                        name = "name",
                        nameSpace = "namespace",
                    )

                coEvery { configRepository.existsById(studyDeploymentId.stringRepresentation) } returns false
                coEvery { objectMapper.valueToTree<JsonNode>(configuration) } returns mockk()
                coEvery {
                    configRepository.save(any())
                } returns DataStreamConfiguration(studyDeploymentId.stringRepresentation, mockk())

                val dataStreamIds = listOf(wsDataStreamId)
                coEvery { dataStreamIdRepository.saveAll(dataStreamIds) } returns dataStreamIds

                sut.openDataStreams(configuration)

                coVerify { configRepository.save(any()) }
                coVerify { dataStreamIdRepository.saveAll(dataStreamIds) }
            }

        @Test
        fun `throw IllegalStateException when configuration already exists`() =
            runTest {
                val studyDeploymentId = UUID.randomUUID()
                val configuration =
                    DataStreamsConfiguration(
                        studyDeploymentId,
                        setOf(
                            DataStreamsConfiguration.ExpectedDataStream(
                                "deviceRole",
                                DataType("namespace", "name"),
                            ),
                        ),
                    )

                coEvery { configRepository.existsById(studyDeploymentId.stringRepresentation) } returns true

                assertThrows<IllegalStateException> {
                    sut.openDataStreams(configuration)
                }
            }

// TODO: Fix unit tests
//        @Test
        fun `log and save new data stream configuration`() =
            runTest {
                val studyDeploymentId = UUID.randomUUID()
                val configuration =
                    DataStreamsConfiguration(
                        studyDeploymentId,
                        setOf(
                            DataStreamsConfiguration.ExpectedDataStream(
                                "deviceRole",
                                DataType("namespace", "name"),
                            ),
                        ),
                    )

                val wsDataStreamId =
                    dk.cachet.carp.webservices.datastream.domain.DataStreamId(
                        id = 0,
                        studyDeploymentId = studyDeploymentId.stringRepresentation,
                        deviceRoleName = "deviceRole",
                        name = "name",
                        nameSpace = "namespace",
                    )

                coEvery { configRepository.existsById(studyDeploymentId.stringRepresentation) } returns false
                coEvery { objectMapper.valueToTree<JsonNode>(configuration) } returns mockk()
                coEvery { configRepository.save(any()) } returnsArgument 0

                val dataStreamIds = listOf(wsDataStreamId)
                coEvery { dataStreamIdRepository.saveAll(dataStreamIds) } returns dataStreamIds

                sut.openDataStreams(configuration)

                coVerify { configRepository.save(any()) }
                coVerify { dataStreamIdRepository.saveAll(dataStreamIds) }
            }
    }

    @Nested
    inner class CloseDataStreams {
        @Test
        fun `close data streams when valid studyDeploymentIds are provided`() =
            runTest {
                val studyDeploymentId1 = UUID.randomUUID()
                val studyDeploymentId2 = UUID.randomUUID()
                val config1 = mockk<DataStreamConfiguration>(relaxed = true)
                val config2 = mockk<DataStreamConfiguration>(relaxed = true)

                coEvery {
                    configRepository.getConfigurationsForIds(
                        listOf(studyDeploymentId1.stringRepresentation, studyDeploymentId2.stringRepresentation),
                    )
                } returns listOf(config1, config2)
                coEvery {
                    configRepository.saveAll(any<List<DataStreamConfiguration>>())
                } returns listOf(config1, config2)

                sut.closeDataStreams(setOf(studyDeploymentId1, studyDeploymentId2))

                coVerify { config1.closed = true }
                coVerify { config2.closed = true }
                coVerify { configRepository.saveAll(listOf(config1, config2)) }
            }
    }
}
