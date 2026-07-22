package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.study.domain.Recruitment
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import kotlin.time.Clock
import dk.cachet.carp.studies.domain.users.Recruitment as CoreRecruitment

class CoreParticipantRepositoryTest {
    private val mockRepository: RecruitmentRepository = mockk()

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

                val sut = CoreParticipantRepository(mockRepository)

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

                val sut = CoreParticipantRepository(mockRepository)

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
                        every { snapshot } returns
                            WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), mockSnapshot)
                    }
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockRecruitment

                val expectedResult =
                    CoreRecruitment.fromSnapshot(
                        WS_JSON.decodeFromString(
                            RecruitmentSnapshot.serializer(),
                            mockRecruitment.snapshot!!,
                        ),
                    )
                val sut = CoreParticipantRepository(mockRepository)

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

                val sut = CoreParticipantRepository(mockRepository)

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
            every { mockRepository.findStudyIdByDeploymentId(deploymentId.stringRepresentation) } returns
                studyId.stringRepresentation

            val sut = CoreParticipantRepository(mockRepository)

            assertEquals(studyId, sut.getStudyIdByDeploymentId(deploymentId))
        }

        @Test
        fun `should return null when no recruitment contains the deployment`() {
            val deploymentId = UUID.randomUUID()
            every { mockRepository.findStudyIdByDeploymentId(deploymentId.stringRepresentation) } returns null

            val sut = CoreParticipantRepository(mockRepository)

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
                        every { snapshot } returns
                            WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), mockSnapshot)
                    }
                coEvery { mockRepository.findRecruitmentByStudyId(mockUUID1.stringRepresentation) } returns
                    mockRecruitment
                coEvery { mockRepository.deleteByStudyId(mockUUID1.stringRepresentation) } just Runs

                val sut = CoreParticipantRepository(mockRepository)

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

                val sut = CoreParticipantRepository(mockRepository)

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

                val sut = CoreParticipantRepository(mockRepository)

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

                val sut = CoreParticipantRepository(mockRepository)

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
    }
}
