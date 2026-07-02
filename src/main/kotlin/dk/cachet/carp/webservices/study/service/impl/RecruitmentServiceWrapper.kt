package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.datastream.service.DataStreamService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.domain.*
import dk.cachet.carp.webservices.study.dto.ParticipantAccountSummaryDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsRequestDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsResponseDto
import dk.cachet.carp.webservices.study.repository.ParticipantAccountQueryRow
import dk.cachet.carp.webservices.study.repository.RecruitmentRepository
import dk.cachet.carp.webservices.study.service.RecruitmentService
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Service
class RecruitmentServiceWrapper(
    private val accountService: AccountService,
    private val dataStreamService: DataStreamService,
    private val recruitmentRepository: RecruitmentRepository,
    private val objectMapper: ObjectMapper,
    services: CoreServiceContainer,
) : RecruitmentService {
    final override val core = services.recruitmentService
    private val deploymentCore = services.deploymentService

    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun inviteUserWithRole(
        studyId: UUID,
        email: String,
        role: Role,
    ) = withContext(Dispatchers.IO + SecurityCoroutineContext()) {
        val accountIdentity = AccountIdentity.fromEmailAddress(email)
        var account = accountService.findByAccountIdentity(accountIdentity)

        if (account == null) {
            LOGGER.info("Account with email $email is not found. Inviting...")
            account = accountService.invite(accountIdentity, role)
        }

        if (account.role!! < role) {
            accountService.addRole(accountIdentity, role)
            LOGGER.info("Account with email $email is granted the role $role.")
        }

        // grant it claims for the study and every deployment within it
        when (role) {
            Role.RESEARCHER ->
                accountService.grant(accountIdentity, setOf(Claim.ManageStudy(studyId)))
            Role.RESEARCH_ASSISTANT ->
                accountService.grant(accountIdentity, setOf(Claim.LimitedManageStudy(studyId)))
            else -> throw IllegalArgumentException("Role $role is not allowed to be invited to a study.")
        }

        LOGGER.info("Account with email $email is added as a ${role.prettyPrint()} to study with id $studyId.")
    }

    override suspend fun removeStudyManager(
        studyId: UUID,
        email: String,
    ): Boolean =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val accountIdentity = AccountIdentity.fromEmailAddress(email)

            val claims =
                setOf(Claim.ManageStudy(studyId)) + setOf(Claim.LimitedManageStudy(studyId))

            val account = accountService.revoke(accountIdentity, claims)

            account.carpClaims?.intersect(claims)?.isEmpty() ?: false
        }

    override suspend fun getParticipants(
        studyId: UUID,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDescending: Boolean?,
        sortBy: ParticipantOrderBy?,
    ): List<Account> =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val serializedParticipants =
                recruitmentRepository.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                    studyId.stringRepresentation,
                    offset,
                    limit,
                    search,
                    isDescending,
                    sortBy,
                )

            if (serializedParticipants.isNullOrEmpty()) return@withContext emptyList()

            val participants =
                objectMapper.readValue(
                    serializedParticipants,
                    object : TypeReference<List<Participant>>() {},
                )

            val accounts = arrayListOf<Account>()
            for (participant in participants) {
                val account = accountService.findByAccountIdentity(participant.accountIdentity)
                accounts.add(account ?: Account.fromAccountIdentity(participant.accountIdentity))
            }

            accounts
        }

    override suspend fun queryParticipantAccounts(
        studyId: UUID,
        request: ParticipantAccountsRequestDto,
    ): ParticipantAccountsResponseDto =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val offset = request.page?.let { page -> request.size?.let { size -> page * size } }
            val participantRows =
                recruitmentRepository.queryParticipantAccounts(
                    studyId.stringRepresentation,
                    offset,
                    request.size,
                    request.search,
                    request.isDeployed,
                    request.sortDirection,
                    request.sortBy,
                )

            val invitedOnByDeploymentId =
                participantRows
                    .mapNotNull { row -> row.deploymentId?.let(UUID::parse) }
                    .toSet()
                    .takeIf { it.isNotEmpty() }
                    ?.let { deploymentIds ->
                        deploymentCore.getStudyDeploymentStatusList(deploymentIds)
                            .associate { status ->
                                status.studyDeploymentId.stringRepresentation to
                                    Instant.fromEpochSeconds(
                                        status.createdOn.epochSeconds,
                                        status.createdOn.nanosecondsOfSecond.toLong(),
                                    )
                            }
                    } ?: emptyMap()

            val content =
                participantRows.map { participantRow ->
                    mapParticipantAccountRow(participantRow, invitedOnByDeploymentId)
                }

            ParticipantAccountsResponseDto(
                page = request.page,
                size = request.size,
                total =
                    recruitmentRepository.countQueryParticipantAccounts(
                        studyId.stringRepresentation,
                        request.search,
                        request.isDeployed,
                    ),
                content = content,
            )
        }

    private suspend fun mapParticipantAccountRow(
        participantRow: ParticipantAccountQueryRow,
        invitedOnByDeploymentId: Map<String, Instant>,
    ): ParticipantAccountSummaryDto {
        val participant =
            objectMapper.readValue(
                participantRow.participantJson,
                Participant::class.java,
            )
        val foundAccount = accountService.findByAccountIdentity(participant.accountIdentity)
        val account = foundAccount ?: Account.fromAccountIdentity(participant.accountIdentity)

        return ParticipantAccountSummaryDto(
            participantId = participant.id.stringRepresentation,
            firstName = account.firstName,
            lastName = account.lastName,
            accountIdentity =
                when (val identity = participant.accountIdentity) {
                    is EmailAccountIdentity -> identity.emailAddress.address
                    is UsernameAccountIdentity -> identity.username.name
                    else -> null
                },
            isDeployed = participantRow.isDeployed,
            invitedOn = participantRow.deploymentId?.let(invitedOnByDeploymentId::get),
            carpUser = foundAccount != null,
        )
    }

    override suspend fun getInactiveDeployments(
        studyId: UUID,
        lastUpdate: Int,
        offset: Int,
        limit: Int,
    ): List<InactiveDeploymentInfo> {
        val timeNow: Instant = Clock.System.now()

        val participantGroupStatusList =
            core.getParticipantGroupStatusList(studyId)
                .filterIsInstance<ParticipantGroupStatus.InDeployment>()

        val inactiveDeploymentInfoList =
            participantGroupStatusList
                .map {
                    val lastDataUpload =
                        dataStreamService.getLatestUpdatedAt(
                            it.studyDeploymentStatus.studyDeploymentId,
                        )
                    InactiveDeploymentInfo(it.id, lastDataUpload)
                }
                .filter {
                    it.dateOfLastDataUpload != null &&
                        it.dateOfLastDataUpload.plus(lastUpdate, DateTimeUnit.HOUR) < timeNow
                }

        if (offset >= 0 && limit > 0) {
            return inactiveDeploymentInfoList.drop(offset * limit).take(limit).sortedBy { it.dateOfLastDataUpload }
        }

        return inactiveDeploymentInfoList.sortedBy { it.dateOfLastDataUpload }
    }

    override fun isParticipant(
        studyId: UUID,
        accountId: UUID,
    ): Boolean =
        runBlocking(SecurityCoroutineContext()) {
            getParticipants(studyId, null, null, null, false, null).any { it.id == accountId.toString() }
        }

    override suspend fun getParticipantGroupsStatus(studyId: UUID): ParticipantGroupsStatus =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val participantGroupStatusList = core.getParticipantGroupStatusList(studyId)
            val lastUploadByDeployment = mutableMapOf<UUID, Instant?>()

            val participantGroupInfoList =
                participantGroupStatusList
                    .filterIsInstance<ParticipantGroupStatus.InDeployment>()
                    .map { groupStatus ->
                        val accountsByParticipant =
                            groupStatus.participants.associateWith { participant ->
                                accountService.findByAccountIdentity(participant.accountIdentity)
                            }

                        val lastDataUpload =
                            if (accountsByParticipant.values.any { it != null }) {
                                lastUploadByDeployment.getOrPut(groupStatus.studyDeploymentStatus.studyDeploymentId) {
                                    dataStreamService.getLatestUpdatedAt(
                                        groupStatus.studyDeploymentStatus.studyDeploymentId,
                                    )
                                }
                            } else {
                                null
                            }

                        val participantAccounts =
                            groupStatus.participants.map { participant ->
                                val participantAccount = ParticipantAccount.fromParticipant(participant)
                                val account = accountsByParticipant[participant]

                                if (account != null) {
                                    participantAccount.lateInitFrom(account)

                                    // TODO: we cannot track this for participants, only for deployments
                                    participantAccount.dateOfLastDataUpload = lastDataUpload
                                }

                                participantAccount
                            }

                        ParticipantGroupInfo(groupStatus.id, groupStatus.studyDeploymentStatus, participantAccounts)
                    }

            ParticipantGroupsStatus(participantGroupInfoList, participantGroupStatusList)
        }
}
