package dk.cachet.carp.webservices.datastream.service.impl

import dk.cachet.carp.common.application.ApplicationData
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.data.infrastructure.DataStreamServiceDecorator
import dk.cachet.carp.deployments.application.users.Participation
import dk.cachet.carp.deployments.domain.users.AccountParticipation
import dk.cachet.carp.deployments.domain.users.ParticipantGroup
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.studies.application.StudyDetails
import dk.cachet.carp.studies.domain.users.ParticipantRepository
import dk.cachet.carp.studies.domain.users.Recruitment
import dk.cachet.carp.studies.infrastructure.StudyServiceDecorator
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.datastream.domain.DataStreamId
import dk.cachet.carp.webservices.datastream.domain.DateTaskQuantityTriple
import dk.cachet.carp.webservices.datastream.dto.DateTaskQuantityTripleDb
import dk.cachet.carp.webservices.datastream.repository.DataStreamIdRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.datastream.service.core.CoreDataStreamService
import dk.cachet.carp.webservices.deployment.service.ParticipationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataStreamServiceTest {
    private val dataStreamIdRepository = mockk<DataStreamIdRepository>()
    private val dataStreamSequenceRepository = mockk<DataStreamSequenceRepository>()
    private val objectMapper = ObjectMapper()
    private val participantRepository = mockk<ParticipantRepository>()
    private val participationService = mockk<ParticipationService>()
    private val studyService = mockk<StudyServiceDecorator>()
    val services: CoreServiceContainer =
        mockk<CoreServiceContainer> {
            every { dataStreamService } returns mockk<DataStreamServiceDecorator>()
            every { studyService } returns this@DataStreamServiceTest.studyService
        }

    @Nested
    inner class ExtractFilesFromZip {
        @BeforeEach
        fun setup() {
            System.setProperty("kotlinx.coroutines.debug", "on")
        }

        @Test
        fun `should extract files from valid zip from generateRandomDataStreamServiceRequest()`() =
            runTest {
                val requestAsJson = generateRandomDataStreamServiceRequest()
                val zipOutputStream = compressData(requestAsJson)

                val services = mockk<CoreServiceContainer>()
                val coreDataStreamService = mockk<CoreDataStreamService>()
                every { services.dataStreamService } returns DataStreamServiceDecorator(coreDataStreamService, mockk())

                assertDoesNotThrow {
                    withContext(Dispatchers.IO) {
                        decompressGzip(zipOutputStream)
                    }
                }
            }

        @Test
        fun `should throw error for invalid zip`() =
            runTest {
                val invalidJsonZip = createInvalidZip()

                val mockFile = mockk<MultipartFile>()
                every { mockFile.originalFilename } returns "invalid.zip"
                every { mockFile.contentType } returns "application/zip"
                every { mockFile.size } returns invalidJsonZip.size.toLong()
                every { mockFile.isEmpty } returns false
                every { mockFile.bytes } returns invalidJsonZip

                val services = mockk<CoreServiceContainer>()
                val coreDataStreamService = mockk<CoreDataStreamService>()
                every { services.dataStreamService } returns
                    DataStreamServiceDecorator(
                        coreDataStreamService,
                        mockk(),
                    )

                assertFailsWith<IOException> {
                    withContext(Dispatchers.IO) {
                        decompressGzip(mockFile.bytes)
                    }
                }
            }

        private fun createInvalidZip(): ByteArray {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val zipOutputStream = ZipOutputStream(byteArrayOutputStream)

            zipOutputStream.putNextEntry(ZipEntry("invalid.json"))
            zipOutputStream.write("invalid json content".toByteArray())
            zipOutputStream.closeEntry()

            zipOutputStream.close()

            return byteArrayOutputStream.toByteArray()
        }

        private fun generateRandomDataStreamServiceRequest(): String {
            val objectMapper = ObjectMapper()

            val writer = StringWriter()
            val jsonGenerator = objectMapper.createGenerator(writer)

            jsonGenerator.writeStartObject()
            jsonGenerator.writeName("__type")
            jsonGenerator.writeString("dk.cachet.carp.data.infrastructure.DataStreamServiceRequest.AppendToDataStreams")
            jsonGenerator.writeName("apiVersion")
            jsonGenerator.writeString("1.1")
            jsonGenerator.writeName("studyDeploymentId")
            jsonGenerator.writeString(UUID.randomUUID().toString())

            jsonGenerator.writeName("batch")
            jsonGenerator.writeStartArray()
            repeat(Random.nextInt(1, 5)) { // Generate 1 to 5 batch objects
                jsonGenerator.writeStartObject()

                jsonGenerator.writeName("dataStream")
                jsonGenerator.writeStartObject()
                jsonGenerator.writeName("studyDeploymentId")
                jsonGenerator.writeString(UUID.randomUUID().toString())
                jsonGenerator.writeName("deviceRoleName")
                jsonGenerator.writeString("Primary Phone")
                jsonGenerator.writeName("dataType")
                jsonGenerator.writeString("dk.cachet.carp.heartbeat")
                jsonGenerator.writeEndObject()

                jsonGenerator.writeName("firstSequenceId")
                jsonGenerator.writeNumber(Random.nextInt(Int.MAX_VALUE))

                jsonGenerator.writeName("measurements")
                jsonGenerator.writeStartArray()
                jsonGenerator.writeStartObject()
                jsonGenerator.writeName("sensorStartTime")
                jsonGenerator.writeNumber(Random.nextLong())
                jsonGenerator.writeName("data")
                jsonGenerator.writeStartObject()
                jsonGenerator.writeName("__type")
                jsonGenerator.writeString("dk.cachet.carp.heartbeat")
                jsonGenerator.writeName("period")
                jsonGenerator.writeNumber(Random.nextInt())
                jsonGenerator.writeName("deviceType")
                jsonGenerator.writeString("dk.cachet.carp.common.application.devices.Smartphone")
                jsonGenerator.writeName("deviceRoleName")
                jsonGenerator.writeString("Primary Phone")
                jsonGenerator.writeEndObject()
                jsonGenerator.writeEndObject()
                jsonGenerator.writeEndArray()

                jsonGenerator.writeName("triggerIds")
                jsonGenerator.writeStartArray()
                jsonGenerator.writeNumber(Random.nextInt())
                jsonGenerator.writeEndArray()

                jsonGenerator.writeEndObject()
            }
            jsonGenerator.writeEndArray()

            jsonGenerator.writeEndObject()

            jsonGenerator.flush()

            return writer.toString()
        }
    }

    @Nested
    inner class GetDataStreamsSummary {
        val sut =
            DataStreamService(
                dataStreamIdRepository,
                dataStreamSequenceRepository,
                objectMapper,
                participantRepository,
                participationService,
                services,
            )

        @BeforeEach
        fun setup() {
            stubProtocolApiLevel("1.0.0")
        }

        private fun stubProtocolApiLevel(level: String?) {
            val mockedProtocolSnapshot =
                level?.let {
                    mockk<StudyProtocolSnapshot> {
                        every { applicationData } returns ApplicationData("""{"protocolApiLevel":"$it"}""")
                    }
                }
            val studyDetails =
                mockk<StudyDetails> {
                    every { protocolSnapshot } returns mockedProtocolSnapshot
                }
            coEvery { studyService.getStudyDetails(any()) } returns studyDetails
        }

        @Test
        fun `throws exception if type is invalid`() {
            runTest {
                val e =
                    assertThrows<IllegalArgumentException> {
                        sut.getDataStreamsSummary(
                            studyId = UUID.randomUUID(),
                            deploymentId = UUID.randomUUID(),
                            participantId = UUID.randomUUID(),
                            scope = "study",
                            type = "invalidType",
                            from = Instant.fromEpochMilliseconds(0),
                            to = Instant.fromEpochMilliseconds(0),
                        )
                    }

                assertTrue { e.message?.contains("Invalid type") == true }
            }
        }

        @Test
        fun `throws exception if scope is invalid`() {
            runTest {
                val e =
                    assertThrows<IllegalArgumentException> {
                        sut.getDataStreamsSummary(
                            studyId = UUID.randomUUID(),
                            deploymentId = UUID.randomUUID(),
                            participantId = UUID.randomUUID(),
                            scope = "invalidType",
                            type = "survey",
                            from = Instant.fromEpochMilliseconds(0),
                            to = Instant.fromEpochMilliseconds(1),
                        )
                    }

                assertTrue { e.message?.contains("Invalid scope") == true }
            }
        }

        @Test
        fun `throws exception if scope is deployment but deploymentId is absent`() {
            runTest {
                val e =
                    assertThrows<IllegalArgumentException> {
                        sut.getDataStreamsSummary(
                            studyId = UUID.randomUUID(),
                            deploymentId = null,
                            participantId = UUID.randomUUID(),
                            scope = "deployment",
                            type = "survey",
                            from = Instant.fromEpochMilliseconds(0),
                            to = Instant.fromEpochMilliseconds(1),
                        )
                    }

                assertTrue { e.message?.contains("Deployment ID must be provided when scope is 'deployment'") == true }
            }
        }

        @Suppress("LongMethod")
        @Test
        fun `should return the data stream summary for deployment scope`() {
            runTest {
                val deploymentId = UUID.randomUUID()
                val from = Instant.fromEpochMilliseconds(0)
                val to = Instant.fromEpochMilliseconds(1000)
                val studyId = UUID.randomUUID()
                val taskType = "survey"

                val mockListOfDataStreamIds =
                    listOf(
                        DataStreamId(id = 1),
                        DataStreamId(id = 2),
                    )

                val mockListOfDateTaskQuantityTripleDbs =
                    listOf(
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(1, 0, ZoneOffset.UTC),
                            "survey1",
                            2,
                        ),
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(2, 0, ZoneOffset.UTC),
                            "survey1",
                            2,
                        ),
                    )

                val mockedListOfDateTaskQuantityTriples =
                    mockListOfDateTaskQuantityTripleDbs.map {
                        DateTaskQuantityTriple(
                            date = Instant.fromEpochMilliseconds(it.date.toInstant(ZoneOffset.UTC).toEpochMilli()),
                            task = it.task,
                            quantity = it.quantity,
                        )
                    }

                every {
                    dataStreamIdRepository.getAllByDeploymentId(
                        deploymentId.toString(),
                    )
                } returns mockListOfDataStreamIds
                every {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                        dataStreamIds = mockListOfDataStreamIds.map { it.id },
                        from = from.toJavaInstant(),
                        to = to.toJavaInstant(),
                        taskType = taskType,
                    )
                } returns mockListOfDateTaskQuantityTripleDbs

                val result =
                    sut.getDataStreamsSummary(
                        studyId = studyId,
                        deploymentId = deploymentId,
                        participantId = null,
                        scope = "deployment",
                        type = taskType,
                        from = from,
                        to = to,
                    )

                assertEquals(result.data, mockedListOfDateTaskQuantityTriples)
                assertEquals(result.studyId, studyId.toString())
                assertEquals(result.deploymentId, deploymentId.toString())
                assertEquals(result.participantId, null)
                assertEquals(result.scope, "deployment")
                assertEquals(result.type, taskType)
                assertEquals(result.from, from)
                assertEquals(result.to, to)
            }
        }

        @Suppress("LongMethod")
        @Test
        fun `should return the data stream summary for deployment scope when protocol api level is v2`() {
            runTest {
                stubProtocolApiLevel("2.0.0")

                val deploymentId = UUID.randomUUID()
                val from = Instant.fromEpochMilliseconds(0)
                val to = Instant.fromEpochMilliseconds(1000)
                val studyId = UUID.randomUUID()
                val taskType = "survey"

                val mockListOfDateTaskQuantityTripleDbs =
                    listOf(
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(1, 0, ZoneOffset.UTC),
                            "survey",
                            2,
                        ),
                    )

                val mockedListOfDateTaskQuantityTriples =
                    mockListOfDateTaskQuantityTripleDbs.map {
                        DateTaskQuantityTriple(
                            date = Instant.fromEpochMilliseconds(it.date.toInstant(ZoneOffset.UTC).toEpochMilli()),
                            task = it.task,
                            quantity = it.quantity,
                        )
                    }

                every {
                    dataStreamIdRepository.getAllIdsByDeploymentIdAndNameSpaceAndName(
                        deploymentId.toString(),
                        "dk.cachet.carp.completedapptask",
                        taskType,
                    )
                } returns listOf(1, 2)

                every {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParametersV2(
                        dataStreamIds = listOf(1, 2),
                        from = from.toJavaInstant(),
                        to = to.toJavaInstant(),
                        completedAppTaskType = "dk.cachet.carp.completedapptask.$taskType",
                    )
                } returns mockListOfDateTaskQuantityTripleDbs

                val result =
                    sut.getDataStreamsSummary(
                        studyId = studyId,
                        deploymentId = deploymentId,
                        participantId = null,
                        scope = "deployment",
                        type = taskType,
                        from = from,
                        to = to,
                    )

                assertEquals(result.data, mockedListOfDateTaskQuantityTriples)
                verify(exactly = 1) {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParametersV2(
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
                verify(exactly = 0) {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }
        }

        @Suppress("LongMethod")
        @Test
        fun `should return the data stream summary for study scope`() {
            runTest {
                val from = Instant.fromEpochMilliseconds(0)
                val to = Instant.fromEpochMilliseconds(1000)
                val studyId = UUID.randomUUID()
                val taskType = "survey"

                val mockRecruitment = mockk<Recruitment>()
                val mockDeploymentId1 = UUID.randomUUID()
                val mockDeploymentId2 = UUID.randomUUID()
                every { mockRecruitment.participantGroups } returns
                    mapOf(
                        mockDeploymentId1 to mockk(),
                        mockDeploymentId2 to mockk(),
                    )

                coEvery { participantRepository.getRecruitment(studyId) } returns mockRecruitment

                coEvery { dataStreamIdRepository.getAllByDeploymentId(mockDeploymentId1.toString()) } returns
                    listOf(
                        DataStreamId(id = 1),
                        DataStreamId(id = 2),
                    )
                coEvery { dataStreamIdRepository.getAllByDeploymentId(mockDeploymentId2.toString()) } returns
                    listOf(
                        DataStreamId(id = 3),
                    )

                val mockListOfDateTaskQuantityTripleDbs =
                    listOf(
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(1, 0, ZoneOffset.UTC),
                            "survey1",
                            2,
                        ),
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(2, 0, ZoneOffset.UTC),
                            "survey1",
                            2,
                        ),
                    )

                val mockedListOfDateTaskQuantityTriples =
                    mockListOfDateTaskQuantityTripleDbs.map {
                        DateTaskQuantityTriple(
                            date = Instant.fromEpochMilliseconds(it.date.toInstant(ZoneOffset.UTC).toEpochMilli()),
                            task = it.task,
                            quantity = it.quantity,
                        )
                    }

                coEvery {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                        dataStreamIds = listOf(1, 2, 3),
                        from = from.toJavaInstant(),
                        to = to.toJavaInstant(),
                        taskType = taskType,
                    )
                } returns mockListOfDateTaskQuantityTripleDbs

                val result =
                    sut.getDataStreamsSummary(
                        studyId = studyId,
                        deploymentId = null,
                        participantId = null,
                        scope = "study",
                        type = taskType,
                        from = from,
                        to = to,
                    )

                assertEquals(result.data, mockedListOfDateTaskQuantityTriples)
                assertEquals(result.studyId, studyId.toString())
                assertEquals(result.deploymentId, null)
                assertEquals(result.participantId, null)
                assertEquals(result.scope, "study")
                assertEquals(result.type, taskType)
                assertEquals(result.from, from)
                assertEquals(result.to, to)
            }
        }

        @Test
        fun `throws exception if scope is participant but participantId is absent`() {
            runTest {
                val e =
                    assertThrows<IllegalArgumentException> {
                        sut.getDataStreamsSummary(
                            studyId = UUID.randomUUID(),
                            deploymentId = UUID.randomUUID(),
                            participantId = null,
                            scope = "participant",
                            type = "survey",
                            from = Instant.fromEpochMilliseconds(0),
                            to = Instant.fromEpochMilliseconds(1),
                        )
                    }

                assertTrue {
                    e.message?.contains("Participant ID must be provided when scope is 'participant'") == true
                }
            }
        }

        @Test
        fun `throws exception if scope is participant but deploymentId is absent`() {
            runTest {
                val e =
                    assertThrows<IllegalArgumentException> {
                        sut.getDataStreamsSummary(
                            studyId = UUID.randomUUID(),
                            deploymentId = null,
                            participantId = UUID.randomUUID(),
                            scope = "participant",
                            type = "survey",
                            from = Instant.fromEpochMilliseconds(0),
                            to = Instant.fromEpochMilliseconds(1),
                        )
                    }

                assertTrue {
                    e.message?.contains("Deployment ID must be provided when scope is 'participant'") == true
                }
            }
        }

        @Test
        fun `throws if from is not before to`() {
            runTest {
                val e =
                    assertThrows<IllegalArgumentException> {
                        sut.getDataStreamsSummary(
                            studyId = UUID.randomUUID(),
                            deploymentId = UUID.randomUUID(),
                            participantId = null,
                            scope = "deployment",
                            type = "survey",
                            from = Instant.fromEpochMilliseconds(1000),
                            to = Instant.fromEpochMilliseconds(0),
                        )
                    }

                assertTrue { e.message?.contains("'from' must be before 'to'.") == true }
            }
        }

        @Test
        fun `returns empty summary when no data streams exist`() {
            runTest {
                val deploymentId = UUID.randomUUID()
                val studyId = UUID.randomUUID()
                val from = Instant.fromEpochMilliseconds(0)
                val to = Instant.fromEpochMilliseconds(1000)
                val taskType = "survey"

                every {
                    dataStreamIdRepository.getAllByDeploymentId(
                        deploymentId.toString(),
                    )
                } returns emptyList()

                val result =
                    sut.getDataStreamsSummary(
                        studyId = studyId,
                        deploymentId = deploymentId,
                        participantId = null,
                        scope = "deployment",
                        type = taskType,
                        from = from,
                        to = to,
                    )

                assertTrue(result.data.isEmpty())
                verify(exactly = 0) {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }
        }

        @Suppress("LongMethod")
        @Test
        fun `should return the data stream summary for participant scope`() {
            runTest {
                val deploymentId = UUID.randomUUID()
                val participantId = UUID.randomUUID()
                val studyId = UUID.randomUUID()
                val from = Instant.fromEpochMilliseconds(0)
                val to = Instant.fromEpochMilliseconds(1000)
                val taskType = "survey"

                val mockParticipation1 = mockk<Participation>()
                every { mockParticipation1.participantId } returns participantId
                val mockParticipation2 = mockk<Participation>()
                every { mockParticipation2.participantId } returns UUID.randomUUID()

                val mockAccountParticipation1 = mockk<AccountParticipation>()
                every { mockAccountParticipation1.participation } returns mockParticipation1
                every { mockAccountParticipation1.assignedPrimaryDeviceRoleNames } returns setOf("primary")
                val mockAccountParticipation2 = mockk<AccountParticipation>()
                every { mockAccountParticipation2.participation } returns mockParticipation2
                every { mockAccountParticipation2.assignedPrimaryDeviceRoleNames } returns setOf("secondary")

                val mockParticipantGroup = mockk<ParticipantGroup>()
                every { mockParticipantGroup.participations } returns
                    setOf(
                        mockAccountParticipation1,
                        mockAccountParticipation2,
                    )

                coEvery { participationService.getParticipantGroup(deploymentId) } returns mockParticipantGroup

                val mockListOfDataStreamIds =
                    mutableListOf(
                        DataStreamId(id = 1),
                        DataStreamId(id = 2),
                    )

                every {
                    dataStreamIdRepository.getAllByStudyDeploymentIdAndDeviceRoleNameIn(
                        deploymentId.stringRepresentation, setOf("primary").toMutableList(),
                    )
                } returns mockListOfDataStreamIds

                val mockListOfDateTaskQuantityTripleDbs =
                    listOf(
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(1, 0, ZoneOffset.UTC),
                            "survey1",
                            2,
                        ),
                        DateTaskQuantityTripleDb(
                            date = LocalDateTime.ofEpochSecond(2, 0, ZoneOffset.UTC),
                            "survey1",
                            2,
                        ),
                    )

                val mockedListOfDateTaskQuantityTriples =
                    mockListOfDateTaskQuantityTripleDbs.map {
                        DateTaskQuantityTriple(
                            date = Instant.fromEpochMilliseconds(it.date.toInstant(ZoneOffset.UTC).toEpochMilli()),
                            task = it.task,
                            quantity = it.quantity,
                        )
                    }

                every {
                    dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                        dataStreamIds = mockListOfDataStreamIds.map { it.id },
                        from = from.toJavaInstant(),
                        to = to.toJavaInstant(),
                        taskType = taskType,
                    )
                } returns mockListOfDateTaskQuantityTripleDbs

                val result =
                    sut.getDataStreamsSummary(
                        studyId = studyId,
                        deploymentId = deploymentId,
                        participantId = participantId,
                        scope = "participant",
                        type = taskType,
                        from = from,
                        to = to,
                    )

                assertEquals(result.data, mockedListOfDateTaskQuantityTriples)
                assertEquals(result.studyId, studyId.toString())
                assertEquals(result.deploymentId, deploymentId.toString())
                assertEquals(result.participantId, participantId.toString())
                assertEquals(result.scope, "participant")
                assertEquals(result.type, taskType)
                assertEquals(result.from, from)
                assertEquals(result.to, to)
            }
        }
    }

    @Nested
    inner class FindLatestUpdatedAtByDataStreamIds {
        lateinit var sut: DataStreamService

        @BeforeEach
        fun setup() {
            sut =
                DataStreamService(
                    dataStreamIdRepository,
                    dataStreamSequenceRepository,
                    objectMapper,
                    participantRepository,
                    participationService,
                    services,
                )

            every {
                dataStreamSequenceRepository.findMaxUpdatedAtByDataStreamIds(any())
            } returns java.time.Instant.ofEpochMilli(1000L)
        }

        @Test
        fun `should return null if dataStreamIds is empty`() {
            val dataStreamIds = listOf<Int>()

            val result = sut.findLatestUpdatedAtByDataStreamIds(dataStreamIds)

            assertEquals(null, result)
        }

        @Test
        fun `should return latest updated as kotlin instant`() {
            val dataStreamIds = listOf(1, 2)

            val result = sut.findLatestUpdatedAtByDataStreamIds(dataStreamIds)

            assertEquals(Instant.fromEpochMilliseconds(1000L), result)
        }
    }

    @Nested
    inner class BuildDataStreamBatch {
        lateinit var sut: DataStreamService

        @BeforeEach
        fun setup() {
            sut =
                DataStreamService(
                    dataStreamIdRepository,
                    dataStreamSequenceRepository,
                    objectMapper,
                    participantRepository,
                    participationService,
                    services,
                )
        }

        @Test
        fun `should throw if dataStreamId not found`() {
            every { dataStreamIdRepository.findByDataStreamId(any()) } returns null
        }
    }

    @Nested
    inner class GetDataStreams {
        lateinit var sut: DataStreamService

        @BeforeEach
        fun setup() {
            sut =
                DataStreamService(
                    dataStreamIdRepository,
                    dataStreamSequenceRepository,
                    objectMapper,
                    participantRepository,
                    participationService,
                    services,
                )
        }

        @Test
        fun `fun should return if empty data streams`() {
            runTest {
                coEvery { dataStreamSequenceRepository.findSequenceIdsByStreamId(any()) } returns emptyList()

                sut.getDataStreams(emptyList(), Path.of("/file.txt"))

                coVerify(exactly = 0) { dataStreamSequenceRepository.findAllByDataStreamIds(any()) }
            }
        }
    }

    @Nested
    inner class ExportDataOrThrow
}
