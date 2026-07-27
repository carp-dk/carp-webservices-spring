package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.deployments.application.StudyDeploymentStatus
import dk.cachet.carp.deployments.infrastructure.DeploymentServiceDecorator
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupRepresentation
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceDecorator
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.datastream.service.DataStreamService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsRequestDto
import dk.cachet.carp.webservices.study.repository.InactiveDeploymentRow
import dk.cachet.carp.webservices.study.repository.ParticipantAccountQueryRow
import dk.cachet.carp.webservices.study.repository.RecruitmentRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant as CoreInstant

private fun participantGroupStatus(
    participants: Set<Participant>,
    deploymentStatus: StudyDeploymentStatus,
): ParticipantGroupStatus.InDeployment =
    ParticipantGroupStatus.InDeployment.fromDeploymentStatus(
        participants,
        emptySet(),
        deploymentStatus,
        ParticipantGroupRepresentation.Default,
    )

class RecruitmentServiceWrapperTest {
    private val accountService: AccountService = mockk()
    private val dataStreamService: DataStreamService = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private val recruitmentRepository: RecruitmentRepository = mockk()
    private val coreRecruitmentService: RecruitmentServiceDecorator = mockk()
    private val coreDeploymentService: DeploymentServiceDecorator = mockk()
    val services: CoreServiceContainer =
        mockk<CoreServiceContainer> {
            every { recruitmentService } returns coreRecruitmentService
            every { deploymentService } returns coreDeploymentService
        }

    private fun createSut(): RecruitmentServiceWrapper =
        RecruitmentServiceWrapper(
            accountService,
            dataStreamService,
            recruitmentRepository,
            objectMapper,
            services,
        )

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

                sut.inviteUserWithRole(mockStudyId, mockEmail, Role.RESEARCHER)

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

                sut.inviteUserWithRole(mockStudyId, mockEmail, Role.RESEARCHER)

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
                    sut.inviteUserWithRole(mockStudyId, mockEmail, Role.RESEARCHER)
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

                sut.inviteUserWithRole(mockStudyId, mockEmail, Role.RESEARCHER)

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

                val result = sut.removeStudyManager(mockStudyId, mockEmail)

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

                val result = sut.removeStudyManager(mockStudyId, mockEmail)

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

                val result = sut.getParticipants(mockStudyId, null, null, null, null)

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

                val result = sut.getParticipants(mockStudyId, null, null, null, null)

                assertEquals(mockParticipants.size, result.size)
                assertEquals(result.get(0), a1)
                assertEquals(result.get(1), a2)
                assertEquals(result.get(2), a3)
            }
        }
    }

    @Nested
    inner class QueryParticipantAccounts {
        @Test
        @Suppress("LongMethod")
        fun `maps deployed state and carp user flag in response content`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val request = ParticipantAccountsRequestDto(page = 0, size = 2)

                val ai1 = EmailAccountIdentity("alpha@example.com")
                val ai2 = EmailAccountIdentity("bravo@example.com")
                val p1 = Participant(ai1)
                val p2 = Participant(ai2)
                val invitedOn = Instant.parse("2024-10-23T08:41:07.850883Z")

                val account1 =
                    Account(
                        firstName = "Alice",
                        lastName = "A",
                        email = ai1.emailAddress.address,
                    )

                val participantRows =
                    listOf(
                        ParticipantAccountQueryRow("""{"stub":1}""", true, "11111111-1111-1111-1111-111111111111"),
                        ParticipantAccountQueryRow("""{"stub":2}""", false, null),
                    )

                stubQueryParticipantAccounts(
                    studyId = mockStudyId,
                    offset = 0,
                    size = 2,
                    isDeployed = null,
                    participantRows = participantRows,
                    total = 7,
                )
                coEvery {
                    objectMapper.readValue(
                        participantRows[0].participantJson,
                        Participant::class.java,
                    )
                } returns p1
                coEvery {
                    objectMapper.readValue(
                        participantRows[1].participantJson,
                        Participant::class.java,
                    )
                } returns p2
                coEvery { accountService.findByAccountIdentity(ai1) } returns account1
                coEvery { accountService.findByAccountIdentity(ai2) } returns null
                val deploymentId = UUID.parse("11111111-1111-1111-1111-111111111111")
                coEvery { coreDeploymentService.getStudyDeploymentStatusList(setOf(deploymentId)) } returns
                    listOf(
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns deploymentId
                            every { createdOn } returns CoreInstant.parse(invitedOn.toString())
                        },
                    )

                val sut = createSut()

                val result = sut.queryParticipantAccounts(mockStudyId, request)

                assertEquals(7, result.total)
                assertEquals(2, result.content.size)
                assertEquals(p1.id.stringRepresentation, result.content[0].participantId)
                assertEquals("Alice", result.content[0].firstName)
                assertEquals(ai1.emailAddress.address, result.content[0].accountIdentity)
                assertTrue(result.content[0].isDeployed)
                assertEquals(invitedOn, result.content[0].invitedOn)
                assertTrue(result.content[0].carpUser)
                assertEquals(p2.id.stringRepresentation, result.content[1].participantId)
                assertEquals(ai2.emailAddress.address, result.content[1].accountIdentity)
                assertFalse(result.content[1].isDeployed)
                assertEquals(null, result.content[1].invitedOn)
                assertFalse(result.content[1].carpUser)
            }
        }

        @Test
        fun `uses filtered count for deployed query`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val request = ParticipantAccountsRequestDto(page = 1, size = 1, isDeployed = true)
                val participantRows =
                    listOf(
                        ParticipantAccountQueryRow("""{"stub":1}""", true, "11111111-1111-1111-1111-111111111111"),
                    )

                val ai1 = EmailAccountIdentity("deployed@example.com")
                val participant = Participant(ai1)
                val invitedOn = Instant.parse("2024-10-23T08:41:07.850883Z")

                stubQueryParticipantAccounts(
                    studyId = mockStudyId,
                    offset = 1,
                    size = 1,
                    isDeployed = true,
                    participantRows = participantRows,
                    total = 1,
                )
                coEvery {
                    objectMapper.readValue(
                        participantRows[0].participantJson,
                        Participant::class.java,
                    )
                } returns participant
                coEvery { accountService.findByAccountIdentity(ai1) } returns
                    Account(email = ai1.emailAddress.address)
                val deploymentId = UUID.parse("11111111-1111-1111-1111-111111111111")
                coEvery { coreDeploymentService.getStudyDeploymentStatusList(setOf(deploymentId)) } returns
                    listOf(
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns deploymentId
                            every { createdOn } returns CoreInstant.parse(invitedOn.toString())
                        },
                    )

                val sut = createSut()

                val result = sut.queryParticipantAccounts(mockStudyId, request)

                assertEquals(1, result.total)
                assertEquals(invitedOn, result.content.single().invitedOn)
                coVerify(exactly = 1) {
                    recruitmentRepository.countQueryParticipantAccounts(
                        mockStudyId.stringRepresentation,
                        null,
                        true,
                    )
                }
            }
        }

        @Test
        fun `flags anonymous row as carp user without a Keycloak lookup`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val request = ParticipantAccountsRequestDto(page = 0, size = 2)

                val ai1 = EmailAccountIdentity("alpha@example.com")
                val uai2 = UsernameAccountIdentity("anonymous-2")
                val p1 = Participant(ai1)
                val p2 = Participant(uai2)

                val account1 =
                    Account(
                        firstName = "Alice",
                        lastName = "A",
                        email = ai1.emailAddress.address,
                    )

                val participantRows =
                    listOf(
                        ParticipantAccountQueryRow("""{"stub":1}""", false, null),
                        ParticipantAccountQueryRow("""{"stub":2}""", true, null),
                    )

                stubQueryParticipantAccounts(
                    studyId = mockStudyId,
                    offset = 0,
                    size = 2,
                    isDeployed = null,
                    participantRows = participantRows,
                    total = 2,
                )
                coEvery {
                    objectMapper.readValue(participantRows[0].participantJson, Participant::class.java)
                } returns p1
                coEvery {
                    objectMapper.readValue(participantRows[1].participantJson, Participant::class.java)
                } returns p2
                coEvery { accountService.findByAccountIdentity(ai1) } returns account1

                val sut = createSut()

                val result = sut.queryParticipantAccounts(mockStudyId, request)

                // Only the email row triggers a Keycloak lookup; the anonymous row is resolved locally.
                coVerify(exactly = 1) { accountService.findByAccountIdentity(any()) }
                coVerify(exactly = 1) { accountService.findByAccountIdentity(ai1) }

                assertEquals(2, result.content.size)
                assertEquals(ai1.emailAddress.address, result.content[0].accountIdentity)
                assertTrue(result.content[0].carpUser)

                val anonymous = result.content[1]
                assertEquals(p2.id.stringRepresentation, anonymous.participantId)
                assertEquals(uai2.username.name, anonymous.accountIdentity)
                assertNull(anonymous.firstName)
                assertNull(anonymous.lastName)
                assertTrue(anonymous.carpUser)
            }
        }

        @Suppress("LongParameterList")
        private fun stubQueryParticipantAccounts(
            studyId: UUID,
            offset: Int,
            size: Int,
            isDeployed: Boolean?,
            participantRows: List<ParticipantAccountQueryRow>,
            total: Int,
        ) {
            coEvery {
                recruitmentRepository.queryParticipantAccounts(
                    studyId.stringRepresentation,
                    offset,
                    size,
                    null,
                    isDeployed,
                    null,
                    null,
                )
            } returns participantRows
            coEvery {
                recruitmentRepository.countQueryParticipantAccounts(
                    studyId.stringRepresentation,
                    null,
                    isDeployed,
                )
            } returns total
        }
    }

    @Nested
    inner class GetInactiveDeployments {
        // The filtering/sorting/paging now happens in one SQL aggregate (see
        // RecruitmentRepositoryImpl.findInactiveDeployments, covered by its Postgres test). Here we
        // verify the wrapper's own responsibilities: threshold math, page-index translation, and mapping.

        @Test
        fun `maps repository rows to InactiveDeploymentInfo preserving order`() {
            runTest {
                val dep1 = UUID.randomUUID()
                val dep2 = UUID.randomUUID()
                val row1 = InactiveDeploymentRow(dep1.stringRepresentation, java.time.Instant.ofEpochSecond(100))
                val row2 = InactiveDeploymentRow(dep2.stringRepresentation, java.time.Instant.ofEpochSecond(200))
                every {
                    recruitmentRepository.findInactiveDeployments(any(), any(), any(), any())
                } returns listOf(row1, row2)

                val result =
                    createSut().getInactiveDeployments(UUID.randomUUID(), lastUpdate = 24, offset = 0, limit = -1)

                assertEquals(2, result.size)
                assertEquals(dep1.stringRepresentation, result[0].deploymentId.stringRepresentation)
                assertEquals(Instant.fromEpochSeconds(100), result[0].dateOfLastDataUpload)
                assertEquals(dep2.stringRepresentation, result[1].deploymentId.stringRepresentation)
                assertEquals(Instant.fromEpochSeconds(200), result[1].dateOfLastDataUpload)
            }
        }

        @Test
        fun `passes a threshold of now minus lastUpdate hours`() {
            runTest {
                val threshold = slot<java.time.Instant>()
                every {
                    recruitmentRepository.findInactiveDeployments(any(), capture(threshold), any(), any())
                } returns emptyList()

                createSut().getInactiveDeployments(UUID.randomUUID(), lastUpdate = 6, offset = 0, limit = -1)

                val expected = java.time.Instant.now().minusSeconds(6 * 3600)
                val diff = java.time.Duration.between(threshold.captured, expected).abs()
                assertTrue(diff.seconds < 60, "threshold expected ~6h ago, diff was $diff")
            }
        }

        @Test
        fun `translates page index into a row offset when limit is positive`() {
            runTest {
                every {
                    recruitmentRepository.findInactiveDeployments(any(), any(), any(), any())
                } returns emptyList()

                createSut().getInactiveDeployments(UUID.randomUUID(), lastUpdate = 1, offset = 2, limit = 10)

                // offset is a page index, so the row offset is page(2) * size(10) = 20.
                verify { recruitmentRepository.findInactiveDeployments(any(), any(), 20, 10) }
            }
        }

        @Test
        fun `requests all rows (no pagination) when limit is not positive`() {
            runTest {
                every {
                    recruitmentRepository.findInactiveDeployments(any(), any(), any(), any())
                } returns emptyList()

                createSut().getInactiveDeployments(UUID.randomUUID(), lastUpdate = 1, offset = 0, limit = -1)

                verify { recruitmentRepository.findInactiveDeployments(any(), any(), null, null) }
            }
        }
    }

    @Nested
    inner class GetParticipantGroupsStatus {
        @Test
        fun getParticipantGroupsStatuses() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val eai1 = EmailAccountIdentity("1@gmail.com")
                val p1 = Participant(eai1)
                val pgs1 =
                    participantGroupStatus(
                        setOf(p1),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns UUID.randomUUID()
                            every { createdOn } returns CoreInstant.fromEpochSeconds(0)
                            every { startedOn } returns CoreInstant.fromEpochSeconds(0)
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
        fun `getParticipantGroupsStatuses fetches last upload once per deployment`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockDeploymentId = UUID.randomUUID()

                val eai1 = EmailAccountIdentity("1@gmail.com")
                val eai2 = EmailAccountIdentity("2@gmail.com")
                val p1 = Participant(eai1)
                val p2 = Participant(eai2)
                val pgs1 =
                    participantGroupStatus(
                        setOf(p1, p2),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns mockDeploymentId
                            every { createdOn } returns CoreInstant.fromEpochSeconds(0)
                            every { startedOn } returns CoreInstant.fromEpochSeconds(0)
                        },
                    )

                coEvery { accountService.findByAccountIdentity(eai1) } returns
                    Account(email = eai1.emailAddress.address)
                coEvery { accountService.findByAccountIdentity(eai2) } returns
                    Account(email = eai2.emailAddress.address)
                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns listOf(pgs1)
                coEvery { dataStreamService.getLatestUpdatedAt(mockDeploymentId) } returns Instant.fromEpochSeconds(0)

                val sut =
                    RecruitmentServiceWrapper(
                        accountService,
                        dataStreamService,
                        recruitmentRepository,
                        objectMapper,
                        services,
                    )

                sut.getParticipantGroupsStatus(mockStudyId)

                coVerify(exactly = 1) { dataStreamService.getLatestUpdatedAt(mockDeploymentId) }
            }
        }

        @Test
        fun `getsParticipantGroupsStatuses account not found`() {
            runTest {
                val mockStudyId = UUID.randomUUID()

                val eai1 = EmailAccountIdentity("1@gmail.com")
                val p1 = Participant(eai1)
                val pgs1 =
                    participantGroupStatus(
                        setOf(p1),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns UUID.randomUUID()
                            every { createdOn } returns CoreInstant.fromEpochSeconds(0)
                            every { startedOn } returns CoreInstant.fromEpochSeconds(0)
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

        @Test
        fun `resolves anonymous participant locally without a Keycloak lookup`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockDeploymentId = UUID.randomUUID()

                val uai1 = UsernameAccountIdentity("anonymous-1")
                val p1 = Participant(uai1)
                val pgs1 =
                    participantGroupStatus(
                        setOf(p1),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns mockDeploymentId
                            every { createdOn } returns CoreInstant.fromEpochSeconds(0)
                            every { startedOn } returns CoreInstant.fromEpochSeconds(0)
                        },
                    )

                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns listOf(pgs1)
                coEvery { dataStreamService.getLatestUpdatedAt(mockDeploymentId) } returns Instant.fromEpochSeconds(0)

                val sut = createSut()

                val result = sut.getParticipantGroupsStatus(mockStudyId)

                // Anonymous (username-only) accounts are resolved locally: no Keycloak call is made.
                coVerify(exactly = 0) { accountService.findByAccountIdentity(any()) }
                val participant = result.groups.single().participants.single()
                assertEquals(p1.id, participant.participantId)
                assertEquals(Role.PARTICIPANT.toString(), participant.role)
                assertNull(participant.firstName)
                assertNull(participant.lastName)
                assertNull(participant.email)
                // Present account means the deployment's last upload is still populated.
                assertEquals(Instant.fromEpochSeconds(0), participant.dateOfLastDataUpload)
            }
        }

        @Test
        fun `looks up only email participants in a mixed group`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockDeploymentId = UUID.randomUUID()

                val eai1 = EmailAccountIdentity("named@gmail.com")
                val uai2 = UsernameAccountIdentity("anonymous-2")
                val p1 = Participant(eai1)
                val p2 = Participant(uai2)
                val pgs1 =
                    participantGroupStatus(
                        setOf(p1, p2),
                        mockk<StudyDeploymentStatus.Invited>().apply {
                            every { studyDeploymentId } returns mockDeploymentId
                            every { createdOn } returns CoreInstant.fromEpochSeconds(0)
                            every { startedOn } returns CoreInstant.fromEpochSeconds(0)
                        },
                    )

                coEvery { accountService.findByAccountIdentity(eai1) } returns
                    Account(email = eai1.emailAddress.address)
                coEvery {
                    services.recruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns listOf(pgs1)
                coEvery { dataStreamService.getLatestUpdatedAt(mockDeploymentId) } returns Instant.fromEpochSeconds(0)

                val sut = createSut()

                val result = sut.getParticipantGroupsStatus(mockStudyId)

                // Only the email participant triggers a Keycloak lookup.
                coVerify(exactly = 1) { accountService.findByAccountIdentity(any()) }
                coVerify(exactly = 1) { accountService.findByAccountIdentity(eai1) }
                assertEquals(2, result.groups.single().participants.size)
            }
        }
    }

    @Nested
    inner class StopParticipantGroup {
        @Test
        fun `stops the deployment and returns the group status`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockGroupId = UUID.randomUUID()

                val mockSds =
                    mockk<StudyDeploymentStatus.Invited>().apply {
                        every { studyDeploymentId } returns mockGroupId
                        every { createdOn } returns CoreInstant.fromEpochSeconds(0)
                        every { startedOn } returns CoreInstant.fromEpochSeconds(0)
                    }
                val pg = participantGroupStatus(emptySet(), mockSds)

                coEvery {
                    coreRecruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns listOf(pg)
                coEvery { coreDeploymentService.stop(mockGroupId) } returns mockk<StudyDeploymentStatus.Stopped>()

                val sut = createSut()

                val result = sut.stopParticipantGroup(mockStudyId, mockGroupId)

                assertEquals(mockGroupId, result.id)
                coVerify(exactly = 1) { coreDeploymentService.stop(mockGroupId) }
            }
        }

        @Test
        fun `throws and does not stop when the group does not belong to the study`() {
            runTest {
                val mockStudyId = UUID.randomUUID()
                val mockGroupId = UUID.randomUUID()

                coEvery {
                    coreRecruitmentService.getParticipantGroupStatusList(mockStudyId)
                } returns emptyList()

                val sut = createSut()

                assertFailsWith<IllegalArgumentException> {
                    sut.stopParticipantGroup(mockStudyId, mockGroupId)
                }
                coVerify(exactly = 0) { coreDeploymentService.stop(any()) }
            }
        }
    }
}
