package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.StudyStatus
import dk.cachet.carp.studies.domain.Study
import dk.cachet.carp.studies.domain.StudySnapshot
import dk.cachet.carp.studies.infrastructure.StudyServiceDecorator
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.study.repository.CoreStudyRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class StudyServiceWrapperTest {
    private val accountService: AccountService = mockk()
    private val studyRepository: CoreStudyRepository = mockk()
    val services: CoreServiceContainer =
        mockk<CoreServiceContainer> {
            every { studyService } returns mockk<StudyServiceDecorator>()
        }

    @Nested
    inner class ExportDataOrThrow {
        @Test
        fun `should export data`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockDeploymentIds =
                    setOf(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                    )
                val mockTarget = mockk<Path>()
                val mockStudySnapshot = mockk<StudySnapshot>()
                val mockStudySnapshots =
                    setOf(
                        mockStudySnapshot,
                    )
                coEvery { studyRepository.getStudySnapshotById(mockStudyId) } returns mockStudySnapshot
                val sut = StudyServiceWrapper(accountService, studyRepository, services)

                val result = sut.exportDataOrThrow(mockStudyId, mockDeploymentIds, mockTarget)

                assertEquals(mockStudySnapshots, result)
            }
        }

        @Test
        fun `should get studies overview`() {
            runTest {
                val mockAccountId = UUID.randomUUID()
                val mockStudyId1 = UUID.randomUUID()
                val mockStudyId2 = UUID.randomUUID()
                val mockNotStudyId3 = UUID.randomUUID()
                val mockClaim1 = Claim.ManageStudy(mockStudyId1)
                val mockClaim2 = Claim.ManageStudy(mockStudyId2)
                val mockClaim3 = Claim.ProtocolOwner(mockNotStudyId3)
                val mockAccount = mockk<Account>()
                coEvery { mockAccount.carpClaims } returns setOf(mockClaim1, mockClaim2, mockClaim3)
                val mockStudyStatus11 = mockk<StudyStatus.Configuring>(relaxed = true)
                coEvery { mockStudyStatus11.studyId } returns mockStudyId1
                val mockStudyStatus12 = mockk<StudyStatus.Configuring>(relaxed = true)
                coEvery { mockStudyStatus12.studyId } returns mockStudyId1
                val mockStudyStatus21 = mockk<StudyStatus.Configuring>(relaxed = true)
                coEvery { mockStudyStatus21.studyId } returns mockStudyId2
                val mockStudy11 =
                    mockk<Study>(relaxed = true).apply {
                        coEvery { getStatus() } returns mockStudyStatus11
                    }
                val mockStudy12 =
                    mockk<Study>(relaxed = true).apply {
                        coEvery { getStatus() } returns mockStudyStatus12
                    }
                val mockStudy21 =
                    mockk<Study>(relaxed = true).apply {
                        coEvery { getStatus() } returns mockStudyStatus21
                    }
                coEvery { accountService.findByUUID(mockAccountId) } returns mockAccount
                coEvery { studyRepository.findAllByStudyIds(listOf(mockStudyId1, mockStudyId2)) } returns
                    listOf(
                        mockStudy11, mockStudy12, mockStudy21,
                    )

                val sut = StudyServiceWrapper(accountService, studyRepository, services)

                val result = sut.getStudiesOverview(mockAccountId)

                assertEquals(3, result.size)
                assertEquals(mockStudyId1, result[0].studyId)
                assertEquals(mockStudyId1, result[1].studyId)
                assertEquals(mockStudyId2, result[2].studyId)
            }
        }

        @Test
        fun `should throw if account not found`() {
            runTest {
                val mockAccountId = UUID.randomUUID()
                coEvery { accountService.findByUUID(mockAccountId) } returns null
                val sut = StudyServiceWrapper(accountService, studyRepository, services)

                assertFailsWith<IllegalArgumentException> {
                    sut.getStudiesOverview(mockAccountId)
                }
            }
        }

        @Test
        fun `filters out claims`() {
            runTest {
                val mockAccountId = UUID.randomUUID()
                val mockClaim1 = Claim.ProtocolOwner(UUID.randomUUID())
                val mockAccount = mockk<Account>()
                coEvery { accountService.findByUUID(mockAccountId) } returns mockAccount
                coEvery { mockAccount.carpClaims } returns setOf(mockClaim1)
                coEvery { studyRepository.findAllByStudyIds(any()) } returns emptyList()

                val sut = StudyServiceWrapper(accountService, studyRepository, services)

                val result = sut.getStudiesOverview(mockAccountId)

                assertEquals(0, result.size)
                coEvery { studyRepository.findAllByStudyIds(listOf()) }
            }
        }

        @Test
        fun `should put all the info in StudyOverview`() {
            runTest {
                val mockAccountId = UUID.randomUUID()
                val mockStudyId1 = UUID.randomUUID()
                val mockClaim1 = Claim.ManageStudy(mockStudyId1)
                val mockAccount = mockk<Account>()
                coEvery { mockAccount.carpClaims } returns setOf(mockClaim1)
                val mockStudyStatus11 =
                    StudyStatus.Configuring(
                        studyId = mockStudyId1,
                        name = "Study 1",
                        createdOn = Instant.fromEpochSeconds(0),
                        studyProtocolId = UUID.randomUUID(),
                        canSetInvitation = true,
                        canSetStudyProtocol = true,
                        canDeployToParticipants = true,
                        canGoLive = true,
                    )
                val mockStudy11 =
                    mockk<Study>(relaxed = true).apply {
                        coEvery { getStatus() } returns mockStudyStatus11
                    }
                coEvery { accountService.findByUUID(mockAccountId) } returns mockAccount
                coEvery { studyRepository.findAllByStudyIds(listOf(mockStudyId1)) } returns listOf(mockStudy11)

                val sut = StudyServiceWrapper(accountService, studyRepository, services)

                val result = sut.getStudiesOverview(mockAccountId)

                assertEquals(1, result.size)
                assertEquals(mockStudyId1, result[0].studyId)
                assertEquals("Study 1", result[0].name)
                assertEquals(Instant.fromEpochSeconds(0), result[0].createdOn)
                assertEquals(mockStudyStatus11.studyProtocolId, result[0].studyProtocolId)
                assertTrue(result[0].canSetInvitation)
                assertTrue(result[0].canSetStudyProtocol)
                assertTrue(result[0].canDeployToParticipants)
            }
        }
    }
}
