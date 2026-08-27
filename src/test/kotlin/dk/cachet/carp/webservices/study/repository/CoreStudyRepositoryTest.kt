package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.ApplicationData
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.studies.domain.StudySnapshot
import dk.cachet.carp.studies.domain.users.Recruitment
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.consent.repository.ConsentDocumentRepository
import dk.cachet.carp.webservices.dataPoint.repository.DataPointRepository
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import dk.cachet.carp.webservices.export.repository.ExportRepository
import dk.cachet.carp.webservices.file.service.FileService
import dk.cachet.carp.webservices.study.domain.Study
import io.mockk.*
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.ObjectMapper
import kotlin.test.*
import kotlin.time.Clock
import dk.cachet.carp.studies.domain.Study as CoreStudy

class CoreStudyRepositoryTest {
    val studyRepository: StudyRepository = mockk()
    val participantRepository: CoreParticipantRepository = mockk()
    val dataPointRepository: DataPointRepository = mockk()
    val collectionRepository: CollectionRepository = mockk()
    val consentDocumentRepository: ConsentDocumentRepository = mockk()
    val documentRepository: DocumentRepository = mockk()
    val exportRepository: ExportRepository = mockk()
    val objectMapper: ObjectMapper = ObjectMapper()
    val validationMessages: MessageBase = mockk()
    val fileService: FileService = mockk()

    // Relaxed: TransactionTemplate.execute only needs getTransaction()/commit()/rollback() to be callable
    // no-ops here, since these are plain unit tests with no real database transaction to manage.
    val mockTransactionManager: PlatformTransactionManager = mockk(relaxed = true)

    @Nested
    inner class Add {
        @Test
        fun `should add study`() =
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockStudySnapshot =
                    StudySnapshot(
                        id = mockUUID,
                        createdOn = Clock.System.now(),
                        version = 1,
                        ownerId = mockUUID2,
                        name = "name",
                        description = "description",
                        invitation =
                            StudyInvitation(
                                "name",
                                "description",
                                ApplicationData(UUID.randomUUID().stringRepresentation),
                            ),
                        protocolSnapshot = null,
                        isLive = false,
                    )
                val study = CoreStudy.fromSnapshot(mockStudySnapshot)

                every { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns null
                every { studyRepository.save(any()) } returns mockk()

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                coreStudyRepository.add(study)

                verify(exactly = 1) { studyRepository.getByStudyId(mockUUID.stringRepresentation) }
                verify(exactly = 1) {
                    studyRepository.save(
                        match {
                            val snapshot = WS_JSON.decodeFromString(StudySnapshot.serializer(), it.snapshot.toString())
                            snapshot.id == mockStudySnapshot.id &&
                                snapshot.ownerId == mockStudySnapshot.ownerId &&
                                snapshot.name == mockStudySnapshot.name &&
                                snapshot.description == mockStudySnapshot.description &&
                                snapshot.invitation.name == mockStudySnapshot.invitation.name
                        },
                    )
                }
            }

        @Test
        fun `should throw exception if study already exists`() =
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockStudySnapshot =
                    StudySnapshot(
                        id = mockUUID,
                        createdOn = Clock.System.now(),
                        version = 1,
                        ownerId = mockUUID2,
                        name = "name",
                        description = "description",
                        invitation =
                            StudyInvitation(
                                "name",
                                "description",
                                ApplicationData(UUID.randomUUID().stringRepresentation),
                            ),
                        protocolSnapshot = null,
                        isLive = false,
                    )
                val study = CoreStudy.fromSnapshot(mockStudySnapshot)

                coEvery { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns mockk()
                coEvery {
                    validationMessages.get(
                        "study.core.add.exists", mockUUID.stringRepresentation,
                    )
                } returns "Study already exists"

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                assertFailsWith<IllegalStateException> {
                    coreStudyRepository.add(study)
                }

                verify(exactly = 1) { studyRepository.getByStudyId(mockUUID.stringRepresentation) }
                verify(exactly = 0) { studyRepository.save(any()) }
                verify { validationMessages.get("study.core.add.exists", mockUUID.stringRepresentation) }
            }
    }

    @Nested
    inner class GetById {
        @Test
        fun `should return study`() =
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockStudySnapshot =
                    StudySnapshot(
                        id = mockUUID,
                        createdOn = Clock.System.now(),
                        version = 1,
                        ownerId = mockUUID2,
                        name = "name",
                        description = "description",
                        invitation =
                            StudyInvitation(
                                "name",
                                "description",
                                ApplicationData(UUID.randomUUID().stringRepresentation),
                            ),
                        protocolSnapshot = null,
                        isLive = false,
                    )

                val mockStudy =
                    mockk<Study>().apply {
                        every { snapshot } returns
                            objectMapper.readTree(
                                WS_JSON.encodeToString(
                                    StudySnapshot.serializer(), mockStudySnapshot,
                                ),
                            )
                    }

                every { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns mockStudy

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                val result = coreStudyRepository.getById(mockUUID)

                assertNotNull(result)
                assertEquals(mockUUID, result!!.id)
                assertEquals(mockUUID2, result.ownerId)
                assertEquals("name", result.name)
                assertEquals("description", result.description)
                assertFalse(result.isLive)
            }

        @Test
        fun `should return null if study does not exist`() =
            runTest {
                val mockUUID = UUID.randomUUID()

                every { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns null

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                val result = coreStudyRepository.getById(mockUUID)

                assertNull(result)

                verify(exactly = 1) { studyRepository.getByStudyId(mockUUID.stringRepresentation) }
            }
    }

    @Suppress("LongMethod")
    @Nested
    inner class GetForOwner {
        @Test
        fun `should return the list of studies for owner`() {
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockStudySnapshot1 =
                    StudySnapshot(
                        id = mockUUID1,
                        createdOn = Clock.System.now(),
                        version = 1,
                        ownerId = mockUUID,
                        name = "name",
                        description = "description",
                        invitation =
                            StudyInvitation(
                                "name",
                                "description",
                                ApplicationData(UUID.randomUUID().stringRepresentation),
                            ),
                        protocolSnapshot = null,
                        isLive = false,
                    )

                val mockStudySnapshot2 =
                    StudySnapshot(
                        id = mockUUID2,
                        createdOn = Clock.System.now(),
                        version = 1,
                        ownerId = mockUUID,
                        name = "name",
                        description = "description",
                        invitation =
                            StudyInvitation(
                                "name",
                                "description",
                                ApplicationData(UUID.randomUUID().stringRepresentation),
                            ),
                        protocolSnapshot = null,
                        isLive = false,
                    )

                val study1 =
                    Study().apply {
                        snapshot =
                            objectMapper
                                .readTree(WS_JSON.encodeToString(StudySnapshot.serializer(), mockStudySnapshot1))
                    }
                val study2 =
                    Study().apply {
                        snapshot =
                            objectMapper
                                .readTree(WS_JSON.encodeToString(StudySnapshot.serializer(), mockStudySnapshot2))
                    }

                every { studyRepository.findAllByOwnerId(mockUUID.stringRepresentation) } returns listOf(study1, study2)

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                val result = coreStudyRepository.getForOwner(mockUUID)

                assertEquals(2, result.size)
                assertEquals(mockUUID1, result[0].id)
                assertEquals(mockUUID2, result[1].id)
                assertEquals(mockUUID, result[0].ownerId)
                assertEquals(mockUUID, result[1].ownerId)
                assertEquals("name", result[0].name)
                assertEquals("name", result[1].name)
                assertEquals("description", result[0].description)
                assertEquals("description", result[1].description)
            }
        }
    }

    @Suppress("LongMethod")
    @Nested
    inner class Remove {
        @Test
        fun `should remove study`() {
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockUUID3 = 1
                val mockUUID4 = 2

                val mockRecruitment =
                    mockk<Recruitment>().apply {
                        every { participantGroups } returns
                            mapOf(
                                mockUUID1 to mockk(),
                                mockUUID2 to mockk(),
                            )
                    }

                coEvery { participantRepository.getRecruitment(mockUUID) } returns mockRecruitment
                coEvery { collectionRepository.getCollectionIdsByStudyId(mockUUID.stringRepresentation) } returns
                    listOf(
                        mockUUID3, mockUUID4,
                    )
                coEvery { documentRepository.deleteAllByCollectionIds(listOf(mockUUID3, mockUUID4)) } just Runs
                coEvery {
                    collectionRepository.deleteAllByDeploymentIds(
                        listOf(
                            mockUUID1.stringRepresentation, mockUUID2.stringRepresentation,
                        ),
                    )
                } just Runs
                coEvery {
                    consentDocumentRepository.deleteAllByDeploymentIds(
                        listOf(
                            mockUUID1.stringRepresentation, mockUUID2.stringRepresentation,
                        ),
                    )
                } just Runs
                coEvery {
                    dataPointRepository.deleteAllByDeploymentIds(
                        listOf(
                            mockUUID1.stringRepresentation, mockUUID2.stringRepresentation,
                        ),
                    )
                } just Runs
                coEvery { fileService.deleteAllByStudyId(mockUUID.stringRepresentation) } just Runs
                coEvery { exportRepository.deleteByStudyId(mockUUID.stringRepresentation) } just Runs
                coEvery { studyRepository.deleteByStudyId(mockUUID.stringRepresentation) } just Runs

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                val result = coreStudyRepository.remove(mockUUID)

                assertTrue(result)

                coVerify(exactly = 1) { participantRepository.getRecruitment(mockUUID) }
                coVerify(exactly = 1) { collectionRepository.getCollectionIdsByStudyId(mockUUID.stringRepresentation) }
                coVerify(exactly = 1) { documentRepository.deleteAllByCollectionIds(listOf(mockUUID3, mockUUID4)) }
                coVerify(exactly = 1) {
                    collectionRepository.deleteAllByDeploymentIds(
                        listOf(
                            mockUUID1.stringRepresentation,
                            mockUUID2.stringRepresentation,
                        ),
                    )
                }
                coVerify(exactly = 1) {
                    consentDocumentRepository.deleteAllByDeploymentIds(
                        listOf(
                            mockUUID1.stringRepresentation,
                            mockUUID2.stringRepresentation,
                        ),
                    )
                }
                coVerify(exactly = 1) {
                    dataPointRepository.deleteAllByDeploymentIds(
                        listOf(
                            mockUUID1.stringRepresentation,
                            mockUUID2.stringRepresentation,
                        ),
                    )
                }
                coVerify(exactly = 1) { fileService.deleteAllByStudyId(mockUUID.stringRepresentation) }
                coVerify(exactly = 1) { exportRepository.deleteByStudyId(mockUUID.stringRepresentation) }
                coVerify(exactly = 1) { studyRepository.deleteByStudyId(mockUUID.stringRepresentation) }
            }
        }
    }

    @Nested
    inner class GetWSStudyById {
        @Test
        fun `should return study`() {
            runTest {
                val mockUUID = UUID.randomUUID()

                val mockStudy = mockk<Study>()
                coEvery { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns mockStudy

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                val result = coreStudyRepository.getWSStudyById(mockUUID)

                assertEquals(mockStudy, result)
            }
        }

        @Test
        fun `should throw exception if study does not exist`() {
            runTest {
                val mockUUID = UUID.randomUUID()

                coEvery { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns null
                coEvery {
                    validationMessages.get(
                        "study.core.study.not_found", mockUUID.stringRepresentation,
                    )
                } returns "Study not found"

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                assertFailsWith<IllegalStateException> {
                    coreStudyRepository.getWSStudyById(mockUUID)
                }

                coVerify(exactly = 1) { studyRepository.getByStudyId(mockUUID.stringRepresentation) }
                coVerify(exactly = 1) {
                    validationMessages.get(
                        "study.core.study.not_found",
                        mockUUID.stringRepresentation,
                    )
                }
            }
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `should update`() {
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockUUID1 = UUID.randomUUID()
                val mockStudySnapshot =
                    StudySnapshot(
                        id = mockUUID,
                        createdOn = Clock.System.now(),
                        version = 1,
                        ownerId = mockUUID1,
                        name = "name",
                        description = "description",
                        invitation =
                            StudyInvitation(
                                "name",
                                "description",
                                ApplicationData(UUID.randomUUID().stringRepresentation),
                            ),
                        protocolSnapshot = null,
                        isLive = false,
                    )
                val coreStudy = CoreStudy.fromSnapshot(mockStudySnapshot)
                val study =
                    Study().apply {
                        snapshot =
                            objectMapper.readTree(WS_JSON.encodeToString(StudySnapshot.serializer(), mockStudySnapshot))
                    }

                coEvery { studyRepository.getByStudyId(mockUUID.stringRepresentation) } returns study
                coEvery { studyRepository.save(any()) } returns mockk()

                val coreStudyRepository =
                    CoreStudyRepository(
                        studyRepository,
                        participantRepository,
                        dataPointRepository,
                        collectionRepository,
                        consentDocumentRepository,
                        documentRepository,
                        exportRepository,
                        objectMapper,
                        validationMessages,
                        fileService,
                        mockTransactionManager,
                    )

                coreStudyRepository.update(coreStudy)

                coVerify(exactly = 1) { studyRepository.getByStudyId(mockUUID.stringRepresentation) }
                coVerify(exactly = 1) {
                    studyRepository.save(
                        match {
                            val snapshot = WS_JSON.decodeFromString(StudySnapshot.serializer(), it.snapshot.toString())
                            snapshot.id == mockStudySnapshot.id && snapshot.ownerId == mockStudySnapshot.ownerId &&
                                snapshot.name == mockStudySnapshot.name &&
                                snapshot.description == mockStudySnapshot.description &&
                                snapshot.invitation.name == mockStudySnapshot.invitation.name
                        },
                    )
                }
            }
        }
    }

    @Suppress("LongMethod")
    @Nested
    inner class FindAllByStudyIds {
        @Test
        fun `should find all by study ids`() {
            val mockUUID = UUID.randomUUID()
            val mockUUID1 = UUID.randomUUID()
            val mockUUID2 = UUID.randomUUID()
            val mockStudySnapshot1 =
                StudySnapshot(
                    id = mockUUID1,
                    createdOn = Clock.System.now(),
                    version = 1,
                    ownerId = mockUUID,
                    name = "name",
                    description = "description",
                    invitation =
                        StudyInvitation(
                            "name",
                            "description",
                            ApplicationData(UUID.randomUUID().stringRepresentation),
                        ),
                    protocolSnapshot = null,
                    isLive = false,
                )

            val mockStudySnapshot2 =
                StudySnapshot(
                    id = mockUUID2,
                    createdOn = Clock.System.now(),
                    version = 1,
                    ownerId = mockUUID,
                    name = "name",
                    description = "description",
                    invitation =
                        StudyInvitation(
                            "name",
                            "description",
                            ApplicationData(UUID.randomUUID().stringRepresentation),
                        ),
                    protocolSnapshot = null,
                    isLive = false,
                )

            val study1 =
                Study().apply {
                    snapshot =
                        objectMapper.readTree(WS_JSON.encodeToString(StudySnapshot.serializer(), mockStudySnapshot1))
                }
            val study2 =
                Study().apply {
                    snapshot =
                        objectMapper.readTree(WS_JSON.encodeToString(StudySnapshot.serializer(), mockStudySnapshot2))
                }

            every {
                studyRepository.findAllByStudyIds(
                    listOf(
                        mockUUID1.stringRepresentation,
                        mockUUID2.stringRepresentation,
                    ),
                )
            } returns listOf(study1, study2)

            val coreStudyRepository =
                CoreStudyRepository(
                    studyRepository,
                    participantRepository,
                    dataPointRepository,
                    collectionRepository,
                    consentDocumentRepository,
                    documentRepository,
                    exportRepository,
                    objectMapper,
                    validationMessages,
                    fileService,
                    mockTransactionManager,
                )

            val result = coreStudyRepository.findAllByStudyIds(listOf(mockUUID1, mockUUID2))

            assertEquals(2, result.size)
            assertEquals(mockStudySnapshot1.name, result[0].name)
            assertEquals(mockStudySnapshot2.name, result[1].name)
        }
    }
}
