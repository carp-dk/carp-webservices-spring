package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupRepresentation
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.study.domain.Recruitment
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizationStore
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizer
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentRows
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import kotlin.test.*
import kotlin.time.Clock
import dk.cachet.carp.studies.domain.users.Recruitment as CoreRecruitment

class CoreParticipantRepositoryTest {
    private val mockRepository: RecruitmentRepository = mockk()
    private val mockStore: RecruitmentNormalizationStore = mockk(relaxUnitFun = true)

    // Relaxed: TransactionTemplate.execute only needs getTransaction()/commit()/rollback() to be callable
    // no-ops here, since these are plain unit tests with no real database transaction to manage.
    private val mockTransactionManager: PlatformTransactionManager = mockk(relaxed = true)

    /** No normalized rows — the snapshots under test carry no participants or groups. */
    private val noRows = RecruitmentRows(emptyList(), emptyList(), emptyList())

    @Nested
    inner class AddRecruitment {
        @Test
        fun `should add recruitment`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockSnapshot =
                    RecruitmentSnapshot(
                        id = mockUUID1,
                        studyId = mockUUID2,
                        version = 1,
                        studyProtocol = null,
                        createdOn = Clock.System.now(),
                        invitation = null,
                    )
                val mockCoreRecruitment =
                    mockk<CoreRecruitment>().apply {
                        every { studyId } returns mockUUID1
                        every { getSnapshot() } returns mockSnapshot
                    }
                val mockExistingRecruitment = null
                val mockSavedRecruitment = mockk<Recruitment>(relaxed = true)
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockExistingRecruitment
                coEvery { mockRepository.save(ofType<Recruitment>()) } returns mockSavedRecruitment

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                sut.addRecruitment(mockCoreRecruitment)

                verify { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) }
                verify {
                    mockRepository.save(
                        match {
                            val snapshot =
                                it.snapshot?.let {
                                        it1 ->
                                    WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), it1)
                                }
                            snapshot == mockSnapshot
                        },
                    )
                }
            }
        }

        @Test
        fun `should throw exception when recruitment already exists`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockSnapshot =
                    RecruitmentSnapshot(
                        id = mockUUID1,
                        studyId = mockUUID2,
                        version = 1,
                        studyProtocol = null,
                        createdOn = Clock.System.now(),
                        invitation = null,
                    )
                val mockCoreRecruitment =
                    mockk<CoreRecruitment>().apply {
                        every { studyId } returns mockUUID1
                        every { getSnapshot() } returns mockSnapshot
                    }
                val mockExistingRecruitment = mockk<Recruitment>()
                val mockSavedRecruitment = mockk<Recruitment>(relaxed = true)
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockExistingRecruitment
                coEvery { mockRepository.save(ofType<Recruitment>()) } returns mockSavedRecruitment

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                assertThrows<IllegalStateException> {
                    sut.addRecruitment(mockCoreRecruitment)
                }

                verify { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) }
                verify(exactly = 0) { mockRepository.save(ofType<Recruitment>()) }
            }
        }
    }

    @Nested
    inner class GetRecruitment {
        @Test
        fun `should get recruitment`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockSnapshot =
                    RecruitmentSnapshot(
                        id = mockUUID1,
                        studyId = mockUUID2,
                        version = 1,
                        studyProtocol = null,
                        createdOn = Clock.System.now(),
                        invitation = null,
                    )
                val mockRecruitment =
                    mockk<Recruitment>().apply {
                        every { id } returns 1
                        every { snapshot } returns
                            WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), mockSnapshot)
                    }
                every { mockStore.readRows(1) } returns noRows
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockRecruitment

                val expectedResult =
                    CoreRecruitment.fromSnapshot(
                        WS_JSON.decodeFromString(
                            RecruitmentSnapshot.serializer(),
                            mockRecruitment.snapshot!!,
                        ),
                    )
                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                val result = sut.getRecruitment(mockUUID1)

                assertEquals(expectedResult.getSnapshot(), result!!.getSnapshot())
            }
        }

        @Test
        fun `should return null when recruitment is not found`() {
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockRecruitment = null
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID.stringRepresentation) } returns
                    mockRecruitment

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                val result = sut.getRecruitment(mockUUID)

                assertNull(result)
                verify { mockRepository.findRecruitmentByStudyId(mockUUID.stringRepresentation) }
            }
        }
    }

    @Nested
    inner class GetStudyIdByDeploymentId {
        @Test
        fun `should map the resolved study id to a UUID`() {
            val deploymentId = UUID.randomUUID()
            val studyId = UUID.randomUUID()
            every { mockRepository.findStudyIdByNormalizedGroupId(deploymentId.stringRepresentation) } returns
                studyId.stringRepresentation

            val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

            assertEquals(studyId, sut.getStudyIdByDeploymentId(deploymentId))
        }

        @Test
        fun `should return null when no recruitment contains the deployment`() {
            val deploymentId = UUID.randomUUID()
            every { mockRepository.findStudyIdByNormalizedGroupId(deploymentId.stringRepresentation) } returns null

            val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

            assertNull(sut.getStudyIdByDeploymentId(deploymentId))
        }
    }

    @Nested
    inner class RemoveStudy {
        @Test
        fun `should remove study`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockSnapshot =
                    RecruitmentSnapshot(
                        id = mockUUID1,
                        studyId = mockUUID2,
                        version = 1,
                        studyProtocol = null,
                        createdOn = Clock.System.now(),
                        invitation = null,
                    )
                val mockRecruitment =
                    mockk<Recruitment>().apply {
                        every { id } returns 1
                        every { snapshot } returns
                            WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), mockSnapshot)
                    }
                every { mockStore.readRows(1) } returns noRows
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockRecruitment
                coEvery { mockRepository.deleteByStudyId(mockUUID1.stringRepresentation) } just Runs

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                val result = sut.removeStudy(mockUUID1)

                assertTrue(result)
                verify { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) }
                verify { mockRepository.deleteByStudyId(mockUUID1.stringRepresentation) }
            }
        }

        @Test
        fun `should return false when recruitment is not found`() {
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockRecruitment = null
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID.stringRepresentation) } returns
                    mockRecruitment

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                val result = sut.removeStudy(mockUUID)

                assertFalse(result)
                verify { mockRepository.findRecruitmentByStudyId(mockUUID.stringRepresentation) }
                verify(exactly = 0) { mockRepository.deleteByStudyId(mockUUID.stringRepresentation) }
            }
        }
    }

    @Nested
    inner class UpdateRecruitment {
        @Test
        fun `should throw if recruitment not found`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockRecruitment =
                    mockk<CoreRecruitment>().apply {
                        every { studyId } returns mockUUID1
                    }

                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns null

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                assertThrows<ResourceNotFoundException> {
                    sut.updateRecruitment(mockRecruitment)
                }

                verify(exactly = 0) { mockRepository.save(any()) }
            }
        }

        @Test
        fun `should update recruitment`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val newMockSnapshot =
                    RecruitmentSnapshot(
                        id = mockUUID1,
                        studyId = mockUUID2,
                        version = 1,
                        studyProtocol = null,
                        createdOn = Clock.System.now(),
                        invitation = null,
                    )
                val mockCoreRecruitment =
                    mockk<CoreRecruitment>().apply {
                        every { studyId } returns mockUUID1
                        every { getSnapshot() } returns newMockSnapshot
                        // The version this command's in-memory aggregate was loaded from; matches what
                        // lockAndGetVersion reports below, so the concurrency check passes.
                        every { fromSnapshotVersion } returns 2
                    }
                val oldMockSnapshot =
                    RecruitmentSnapshot(
                        id = mockUUID1,
                        studyId = mockUUID2,
                        version = 2,
                        studyProtocol = null,
                        createdOn = Clock.System.now(),
                        invitation = null,
                    )
                val mockRecruitmentFound =
                    Recruitment().apply {
                        snapshot = WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), oldMockSnapshot)
                    }

                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockRecruitmentFound
                coEvery { mockRepository.save(ofType<Recruitment>()) } returns mockk()
                every { mockStore.lockAndGetVersion(any()) } returns 2

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                sut.updateRecruitment(mockCoreRecruitment)

                verify { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) }
                verify {
                    mockRepository.save(
                        match {
                            val snapshot =
                                it.snapshot?.let {
                                        it1 ->
                                    WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), it1)
                                }
                            snapshot == newMockSnapshot
                        },
                    )
                }
            }
        }

        @Test
        fun `should refuse to overwrite when the recruitment changed since it was read`() {
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockUUID2 = UUID.randomUUID()
                val mockCoreRecruitment =
                    mockk<CoreRecruitment>().apply {
                        every { studyId } returns mockUUID1
                        // This command's in-memory aggregate was loaded from version 2, but e.g. a
                        // concurrent self-signup append() has since bumped the persisted version to 3.
                        every { fromSnapshotVersion } returns 2
                    }
                val mockRecruitmentFound = Recruitment(id = 5)
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockRecruitmentFound
                every { mockStore.lockAndGetVersion(5) } returns 3

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                assertThrows<ConflictException> { sut.updateRecruitment(mockCoreRecruitment) }

                verify(exactly = 0) { mockRepository.save(any()) }
                verify(exactly = 0) { mockStore.replace(any(), any()) }
            }
        }
    }

    /**
     * Participant and group data lives in the normalized tables; the blob keeps only the envelope.
     * These cover the split itself — the other cases above use recruitments with neither.
     */
    @Nested
    inner class NormalizedStore {
        private val studyId = UUID.randomUUID()
        private val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        private val group =
            StagedParticipantGroup(UUID.randomUUID(), ParticipantGroupRepresentation("Group A")).apply {
                addParticipants(setOf(AssignedParticipantRoles(alice.id, AssignedTo.All)))
            }
        private val populatedSnapshot =
            RecruitmentSnapshot(
                id = UUID.randomUUID(),
                studyId = studyId,
                version = 1,
                studyProtocol = null,
                createdOn = Clock.System.now(),
                invitation = null,
                participants = setOf(alice),
                participantGroups = mapOf(group.id to group),
            )

        @Test
        fun `add writes rows to the store and only the envelope to the blob`() {
            runTest {
                val coreRecruitment = mockk<CoreRecruitment>()
                every { coreRecruitment.studyId } returns studyId
                every { coreRecruitment.getSnapshot() } returns populatedSnapshot
                coEvery { mockRepository.findRecruitmentByStudyId(studyId.stringRepresentation) } returns null
                coEvery { mockRepository.save(ofType<Recruitment>()) } returns
                    mockk<Recruitment>().apply { every { id } returns 7 }

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                sut.addRecruitment(coreRecruitment)

                verify {
                    mockRepository.save(
                        match {
                            val persisted =
                                WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), it.snapshot!!)
                            persisted.participants.isEmpty() && persisted.participantGroups.isEmpty()
                        },
                    )
                }
                verify {
                    mockStore.replace(
                        7,
                        match { it.participants.single().participantId == alice.id.stringRepresentation },
                    )
                }
            }
        }

        @Test
        fun `read reconstructs participants and groups from the rows`() {
            runTest {
                val envelope = populatedSnapshot.copy(participants = emptySet(), participantGroups = emptyMap())
                val stored =
                    mockk<Recruitment>().apply {
                        every { id } returns 7
                        every { snapshot } returns
                            WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), envelope)
                    }
                coEvery { mockRepository.findRecruitmentByStudyId(studyId.stringRepresentation) } returns stored
                val rows = RecruitmentNormalizer.decompose(populatedSnapshot)
                every { mockStore.readRows(7) } returns
                    RecruitmentRows(rows.participants, rows.groups, rows.members)

                val sut = CoreParticipantRepository(mockRepository, mockStore, mockTransactionManager)

                val result = sut.getRecruitment(studyId)

                assertEquals(populatedSnapshot, result!!.getSnapshot())
            }
        }
    }
}
