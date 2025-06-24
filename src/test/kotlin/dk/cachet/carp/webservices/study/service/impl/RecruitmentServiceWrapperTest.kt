package dk.cachet.carp.webservices.study.service.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.deployments.application.StudyDeploymentStatus
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceDecorator
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.datastream.service.DataStreamService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.repository.RecruitmentRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested
import kotlin.test.*

class RecruitmentServiceWrapperTest {
    private val accountService: AccountService = mockk()
    private val dataStreamService: DataStreamService = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private val recruitmentRepository: RecruitmentRepository = mockk()
    val services: CoreServiceContainer =
        mockk<CoreServiceContainer> {
            every { recruitmentService } returns mockk<RecruitmentServiceDecorator>()
        }

    @Nested
    inner class InviteResearcher {
        @Test
        fun `researcher is invited if account does not exist`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockEmail = "test@gmail.com"
                val mockAccount =
                    Account(
                        role = Role.RESEARCHER,
                    )

                coEvery { accountService.findByAccountIdentity(ofType<EmailAccountIdentity>()) } returns null
                coEvery { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns Unit
                coEvery { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns mockAccount
                coEvery { accountService.grant(ofType<EmailAccountIdentity>(), any()) } returns mockAccount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                sut.inviteResearcher(mockStudyId, mockEmail)

                coVerify { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
                coVerify(exactly = 0) { accountService.addRole(ofType<EmailAccountIdentity>(), any()) }
                coVerify { accountService.grant(ofType<EmailAccountIdentity>(), setOf(Claim.ManageStudy(mockStudyId))) }
            }
        }

        @Test
        fun `researcher is given role if account exists and has lower role`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockEmail = "test@gmail.com"
                val mockAccount =
                    Account(
                        role = Role.PARTICIPANT,
                    )

                coEvery { accountService.findByAccountIdentity(ofType<EmailAccountIdentity>()) } returns mockAccount
                coEvery { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns Unit
                coEvery { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns mockAccount
                coEvery { accountService.grant(ofType<EmailAccountIdentity>(), any()) } returns mockAccount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                sut.inviteResearcher(mockStudyId, mockEmail)

                coVerify(exactly = 0) { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
                coVerify { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
                coVerify { accountService.grant(ofType<EmailAccountIdentity>(), setOf(Claim.ManageStudy(mockStudyId))) }
            }
        }

        @Test
        fun `throws if account has no role`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockEmail = "test@gmail.com"
                val mockAccount =
                    Account(
                        role = null,
                    )

                coEvery { accountService.findByAccountIdentity(ofType<EmailAccountIdentity>()) } returns mockAccount
                coEvery { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns Unit
                coEvery { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns mockAccount
                coEvery { accountService.grant(ofType<EmailAccountIdentity>(), any()) } returns mockAccount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                assertFailsWith<NullPointerException> {
                    sut.inviteResearcher(mockStudyId, mockEmail)
                }

                coVerify(exactly = 0) { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
                coVerify(exactly = 0) { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
            }
        }

        @Test
        fun `invites researcher with no extras`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockEmail = "test@gmail.com"
                val mockAccount =
                    Account(
                        role = Role.RESEARCHER,
                    )

                coEvery { accountService.findByAccountIdentity(ofType<EmailAccountIdentity>()) } returns mockAccount
                coEvery { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns Unit
                coEvery { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) } returns mockAccount
                coEvery { accountService.grant(ofType<EmailAccountIdentity>(), any()) } returns mockAccount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                sut.inviteResearcher(mockStudyId, mockEmail)

                coVerify(exactly = 0) { accountService.invite(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
                coVerify(exactly = 0) { accountService.addRole(ofType<EmailAccountIdentity>(), Role.RESEARCHER) }
                coVerify { accountService.grant(ofType<EmailAccountIdentity>(), setOf(Claim.ManageStudy(mockStudyId))) }
            }
        }
    }

    @Nested
    inner class RemoveResearcher {
        @Test
        fun `returns true if manages to remove researcher claims`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockEmail = "test@gmail.com"
                val mockAccount =
                    Account(
                        role = Role.RESEARCHER,
                        carpClaims = emptySet(),
                    )

                coEvery { accountService.revoke(ofType<EmailAccountIdentity>(), any()) } returns mockAccount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.removeResearcher(mockStudyId, mockEmail)

                assertTrue(result)
            }
        }

        @Test
        fun `returns false if fails to remove researcher claims`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockEmail = "test@gmail.com"
                val mockAccount =
                    Account(
                        role = Role.RESEARCHER,
                        carpClaims = setOf(Claim.ManageStudy(mockStudyId)),
                    )

                coEvery { accountService.revoke(ofType<EmailAccountIdentity>(), any()) } returns mockAccount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.removeResearcher(mockStudyId, mockEmail)

                assertFalse(result)
            }
        }
    }

    @Nested
    inner class GetParticipants {
        @Test
        fun `returns participants`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val ai1 = EmailAccountIdentity("ai1@gmail.com")
                val ai2 = EmailAccountIdentity("ai2@gmail.com")
                val ai3 = EmailAccountIdentity("ai3@gmail.com")

                val p1 = Participant(ai1)
                val p2 = Participant(ai2)
                val p3 = Participant(ai3)

                val a1 = Account(email = ai1.emailAddress.address)
                val a2 = Account(email = ai2.emailAddress.address)
                val a3 = Account(email = ai3.emailAddress.address)

                val mockParticipants = listOf(p1, p2, p3)
                val serializedMockParticipants = "serialized listOf(p1, p2, p3)"

                coEvery {
                    recruitmentRepository.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                        mockStudyId.stringRepresentation,
                        null,
                        null,
                        null, null, null,
                    )
                } returns serializedMockParticipants
                coEvery {
                    objectMapper.readValue(serializedMockParticipants, ofType<TypeReference<List<Participant>>>())
                } returns mockParticipants
                coEvery { accountService.findByAccountIdentity(ai1) } returns a1
                coEvery { accountService.findByAccountIdentity(ai2) } returns a2
                coEvery { accountService.findByAccountIdentity(ai3) } returns a3

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getParticipants(mockStudyId, null, null, null, null, null)

                assertEquals(mockParticipants.size, result.size)
                assertEquals(result.get(0), a1)
                assertEquals(result.get(1), a2)
                assertEquals(result.get(2), a3)
            }
        }

        @Test
        fun `returns participants when some participant account is not found`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val ai1 = EmailAccountIdentity("ai1@gmail.com")
                val ai2 = EmailAccountIdentity("ai2@gmail.com")
                val ai3 = EmailAccountIdentity("ai3@gmail.com")

                val p1 = Participant(ai1)
                val p2 = Participant(ai2)
                val p3 = Participant(ai3)

                val a1 = Account(email = ai1.emailAddress.address)
                val a2 = Account(email = ai2.emailAddress.address)
                val a3 = Account(email = ai3.emailAddress.address)

                val mockParticipants = listOf(p1, p2, p3)
                val serializedMockParticipants = "serialized listOf(p1, p2, p3)"

                coEvery {
                    recruitmentRepository.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                        mockStudyId.stringRepresentation,
                        null, null, null, null, null,
                    )
                } returns serializedMockParticipants
                coEvery {
                    objectMapper.readValue(serializedMockParticipants, ofType<TypeReference<List<Participant>>>())
                } returns mockParticipants
                coEvery { accountService.findByAccountIdentity(ai1) } returns a1
                coEvery { accountService.findByAccountIdentity(ai2) } returns null
                coEvery { accountService.findByAccountIdentity(ai3) } returns a3

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getParticipants(mockStudyId, null, null, null, null, null)

                assertEquals(mockParticipants.size, result.size)
                assertEquals(result.get(0), a1)
                assertEquals(result.get(1), a2)
                assertEquals(result.get(2), a3)
            }
        }
    }

    @Nested
    inner class CountParticipants {
        @Test
        fun `returns count of participants`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockSearch = "search"

                val mockCount = 3

                coEvery {
                    recruitmentRepository.countRecruitmentParticipantsByStudyIdAndSearch(
                        mockStudyId.stringRepresentation,
                        mockSearch,
                    )
                } returns mockCount

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.countParticipants(mockStudyId, mockSearch)

                assertEquals(mockCount, result)
            }
        }
    }

    @Nested
    inner class GetInactiveDeployments {
        @Test
        fun `inactive deployments are returned`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val mockSdId1 = UUID.randomUUID()
                val mockSds1 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId1
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg1 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds1)
                val mockInstant1 = Instant.fromEpochSeconds(0)

                val mockSdId2 = UUID.randomUUID()
                val mockSds2 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId2
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg2 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds2)
                val mockInstant2 = Instant.fromEpochSeconds(0)

                val mockParticipantGroupStatusList = listOf(pg1, pg2)

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList

                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId1) } returns mockInstant1
                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId2) } returns mockInstant2

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 0, 0, 0)

                assertEquals(2, result.size)
                assertEquals(result.get(0).dateOfLastDataUpload, mockInstant1)
                assertEquals(result.get(1).dateOfLastDataUpload, mockInstant2)
            }
        }

        @Test
        fun `filters out ParticipantGroupStatusList`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val pg1 = mockk<ParticipantGroupStatus.Staged>()
                val pg2 = mockk<ParticipantGroupStatus.Staged>()

                val mockParticipantGroupStatusList = listOf(pg1, pg2)

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 0, 0, 0)
                assertEquals(0, result.size)
            }
        }

        @Test
        fun `filters out active deployments 1`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val mockSdId1 = UUID.randomUUID()
                val mockSds1 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId1
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg1 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds1)
                val mockInstant1 = Instant.fromEpochSeconds(Clock.System.now().epochSeconds + 1)

                val mockParticipantGroupStatusList = listOf(pg1)

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList

                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId1) } returns mockInstant1

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 0, 0, 0)
                assertEquals(0, result.size)
            }
        }

        @Test
        fun `filters out active deployments 2`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val mockSdId1 = UUID.randomUUID()
                val mockSds1 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId1
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg1 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds1)
                val mockInstant1 = Instant.fromEpochSeconds(Clock.System.now().epochSeconds - 1)

                val mockParticipantGroupStatusList = listOf(pg1)

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList

                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId1) } returns mockInstant1

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 1, 0, 0)
                assertEquals(0, result.size)
            }
        }

        @Test
        fun `filters out active deployments 3`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val mockSdId1 = UUID.randomUUID()
                val mockSds1 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId1
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg1 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds1)
                val mockInstant1 = null

                val mockParticipantGroupStatusList = listOf(pg1)
                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList
                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId1) } returns mockInstant1

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 0, 0, 0)
                assertEquals(0, result.size)
            }
        }

        @Test
        fun `returns results sorted`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val mockSdId1 = UUID.randomUUID()
                val mockSds1 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId1
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg1 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds1)
                val mockInstant1 = Instant.fromEpochSeconds(1)

                val mockSdId2 = UUID.randomUUID()
                val mockSds2 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId2
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg2 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds2)
                val mockInstant2 = Instant.fromEpochSeconds(0)

                val mockParticipantGroupStatusList = listOf(pg1, pg2)

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList

                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId1) } returns mockInstant1
                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId2) } returns mockInstant2

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 0, 0, 0)

                assertEquals(2, result.size)
                assertEquals(result.get(0).dateOfLastDataUpload, mockInstant2)
                assertEquals(result.get(1).dateOfLastDataUpload, mockInstant1)
            }
        }

        @Test
        fun `applies offset and limit`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val mockSdId1 = UUID.randomUUID()
                val mockSds1 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId1
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg1 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds1)
                val mockInstant1 = Instant.fromEpochSeconds(0)

                val mockSdId2 = UUID.randomUUID()
                val mockSds2 =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockSdId2
                        every { createdOn } returns Instant.fromEpochSeconds(0)
                        every { startedOn } returns Instant.fromEpochSeconds(0)
                    }
                val pg2 = ParticipantGroupStatus.InDeployment.fromDeploymentStatus(emptySet(), mockSds2)
                val mockInstant2 = Instant.fromEpochSeconds(0)

                val mockParticipantGroupStatusList = listOf(pg1, pg2)

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList

                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId1) } returns mockInstant1
                coEvery { dataStreamService.getLatestUpdatedAt(mockSdId2) } returns mockInstant2

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getInactiveDeployments(mockStudyId, 0, 1, 1)

                assertEquals(1, result.size)
                assertEquals(result.get(0).dateOfLastDataUpload, mockInstant2)
            }
        }
    }

    @Nested
    inner class IsParticipant {
        @Test
        fun `returns true if participant is found`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockAccountId = UUID.randomUUID()

                val ai1 = EmailAccountIdentity("ai1@gmail.com")
                val ai2 = EmailAccountIdentity("ai2@gmail.com")

                val p1 = Participant(ai1)
                val p2 = Participant(ai2)

                val a1 = Account(email = ai1.emailAddress.address, id = mockAccountId.stringRepresentation)
                val a2 = Account(email = ai2.emailAddress.address, id = UUID.randomUUID().stringRepresentation)

                val mockParticipants = listOf(p1, p2)
                val serializedMockParticipants = "serialized listOf(p1, p2)"

                coEvery {
                    objectMapper.readValue(serializedMockParticipants, ofType<TypeReference<List<Participant>>>())
                } returns mockParticipants
                coEvery {
                    recruitmentRepository.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                        mockStudyId.stringRepresentation,
                        any(), any(), any(), any(), any(),
                    )
                } returns serializedMockParticipants
                coEvery { accountService.findByAccountIdentity(ai1) } returns a1
                coEvery { accountService.findByAccountIdentity(ai2) } returns a2

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.isParticipant(mockStudyId, mockAccountId)

                assertTrue(result)
            }
        }

        @Test
        fun `returns false if participant is not found`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockAccountId = UUID.randomUUID()

                val ai1 = EmailAccountIdentity("ai1@gmail.com")
                val ai2 = EmailAccountIdentity("ai2@gmail.com")

                val p1 = Participant(ai1)
                val p2 = Participant(ai2)

                val a1 = Account(email = ai1.emailAddress.address, id = UUID.randomUUID().stringRepresentation)
                val a2 = Account(email = ai2.emailAddress.address, id = UUID.randomUUID().stringRepresentation)

                val mockParticipants = listOf(p1, p2)
                val serializedMockParticipants = "serialized listOf(p1, p2)"

                coEvery {
                    objectMapper.readValue(serializedMockParticipants, ofType<TypeReference<List<Participant>>>())
                } returns mockParticipants
                coEvery {
                    recruitmentRepository.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                        mockStudyId.stringRepresentation,
                        any(), any(), any(), any(), any(),
                    )
                } returns serializedMockParticipants
                coEvery { accountService.findByAccountIdentity(ai1) } returns a1
                coEvery { accountService.findByAccountIdentity(ai2) } returns a2

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.isParticipant(mockStudyId, mockAccountId)

                assertFalse(result)
            }
        }
    }

    @Nested
    inner class GetParticipantGroupsStatus {
        @Test
        fun `getsParticipantGroupsStatuses`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val eai1 = EmailAccountIdentity("1@gmail.com")
                val p1 = Participant(eai1)
                val pgs1 =
                    ParticipantGroupStatus.InDeployment.fromDeploymentStatus(
                        setOf(p1),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns UUID.randomUUID()
                            every { createdOn } returns Instant.fromEpochSeconds(0)
                            every { startedOn } returns Instant.fromEpochSeconds(0)
                        },
                    )
                val a1 = Account(email = eai1.emailAddress.address)

                coEvery { accountService.findByAccountIdentity(eai1) } returns a1
                val mockParticipantGroupStatusList = listOf(pgs1)
                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList
                coEvery { dataStreamService.getLatestUpdatedAt(any()) } returns Instant.fromEpochSeconds(0)

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getParticipantGroupsStatus(mockStudyId)

                assertEquals(1, result.groups.size)
                assertEquals(result.groups.get(0).participants.size, 1)
                assertEquals(result.groups.get(0).participants.get(0).participantId, p1.id)
                assertEquals(result.groups.get(0).participants.get(0).email, eai1.emailAddress.address)
                assertEquals(result.groups.get(0).participantGroupId, pgs1.id)
                assertEquals(result.groups.get(0).deploymentStatus, pgs1.studyDeploymentStatus)
            }
        }

        @Test
        fun `getsParticipantGroupsStatuses account not found`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val eai1 = EmailAccountIdentity("1@gmail.com")
                val p1 = Participant(eai1)
                val pgs1 =
                    ParticipantGroupStatus.InDeployment.fromDeploymentStatus(
                        setOf(p1),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns UUID.randomUUID()
                            every { createdOn } returns Instant.fromEpochSeconds(0)
                            every { startedOn } returns Instant.fromEpochSeconds(0)
                        },
                    )

                coEvery { accountService.findByAccountIdentity(eai1) } returns null
                val mockParticipantGroupStatusList = listOf(pgs1)
                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns mockParticipantGroupStatusList
                coEvery { dataStreamService.getLatestUpdatedAt(any()) } returns Instant.fromEpochSeconds(0)

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getParticipantGroupsStatus(mockStudyId)

                verify(exactly = 0) { dataStreamService.getLatestUpdatedAt(any()) }
                assertEquals(1, result.groups.size)
                assertEquals(result.groups.get(0).participants.size, 1)
                assertEquals(result.groups.get(0).participants.get(0).participantId, p1.id)
                assertEquals(result.groups.get(0).participants.get(0).email, eai1.emailAddress.address)
                assertEquals(result.groups.get(0).participantGroupId, pgs1.id)
                assertEquals(result.groups.get(0).deploymentStatus, pgs1.studyDeploymentStatus)
            }
        }

        @Test
        fun `filters out participantGroupStatusList`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val pgs1 = mockk<ParticipantGroupStatus.Staged>()
                val pgsList = listOf(pgs1)

                coEvery { services.recruitmentService.getParticipantGroupStatusList(mockStudyId) } returns pgsList

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                val result = sut.getParticipantGroupsStatus(mockStudyId)

                assertEquals(0, result.groups.size)
                assertEquals(1, result.groupStatuses.size)
            }
        }
    }
}
