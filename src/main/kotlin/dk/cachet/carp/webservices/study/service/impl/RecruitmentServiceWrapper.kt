package dk.cachet.carp.webservices.study.service.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
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

@Service
class RecruitmentServiceWrapper(
    private val accountService: AccountService,
    private val dataStreamService: DataStreamService,
    private val recruitmentRepository: RecruitmentRepository,
    private val objectMapper: ObjectMapper,
    services: CoreServiceContainer,
) : RecruitmentService {
    final override val core = services.recruitmentService

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
    ): List<Account> =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val serializedParticipants =
                recruitmentRepository.findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
                    studyId.stringRepresentation,
                    offset,
                    limit,
                    search,
                    isDescending,
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

    override suspend fun countParticipants(
        studyId: UUID,
        search: String?,
    ): Int =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val count =
                recruitmentRepository.countRecruitmentParticipantsByStudyIdAndSearch(
                    studyId.stringRepresentation,
                    search,
                )

            count
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
            getParticipants(studyId, null, null, null, false).any { it.id == accountId.toString() }
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
