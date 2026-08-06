package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.deployments.application.StudyDeploymentStatus
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
import dk.cachet.carp.webservices.study.dto.DeploymentStatusCountsDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountSummaryDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsRequestDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsResponseDto
import dk.cachet.carp.webservices.study.repository.ParticipantAccountQueryRow
import dk.cachet.carp.webservices.study.repository.RecruitmentRepository
import dk.cachet.carp.webservices.study.service.RecruitmentService
import kotlinx.coroutines.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

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
        // Generated (anonymous) accounts are username-only with no name/email to enrich, and they always
        // exist, so we skip the per-row Keycloak lookup for them and resolve their fields locally.
        // TODO(account-existence): this ASSUMES the generated account exists (we create it) rather than
        //  verifying it. Long-term, confirm existence cheaply via a per-study Keycloak group
        //  (GET /groups/{id}/members) or a local participant read model. See project_account_lookup_n1.
        val identity = participant.accountIdentity
        val isAnonymous = identity is UsernameAccountIdentity
        val foundAccount = if (isAnonymous) null else accountService.findByAccountIdentity(identity)
        val account = foundAccount ?: Account.fromAccountIdentity(identity)

        return ParticipantAccountSummaryDto(
            participantId = participant.id.stringRepresentation,
            firstName = account.firstName,
            lastName = account.lastName,
            accountIdentity =
                when (identity) {
                    is EmailAccountIdentity -> identity.emailAddress.address
                    is UsernameAccountIdentity -> identity.username.name
                    else -> null
                },
            isDeployed = participantRow.isDeployed,
            invitedOn = participantRow.deploymentId?.let(invitedOnByDeploymentId::get),
            carpUser = isAnonymous || foundAccount != null,
        )
    }

    override suspend fun getInactiveDeployments(
        studyId: UUID,
        lastUpdate: Int,
        offset: Int,
        limit: Int,
    ): List<InactiveDeploymentInfo> =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            // "Inactive" = latest upload older than `lastUpdate` hours, i.e. lastUpload < now - lastUpdate.
            val threshold = Clock.System.now().minus(lastUpdate, DateTimeUnit.HOUR)

            // Filter/sort/page happen in one aggregate query over the normalized recruitment tables
            // (see RecruitmentRepositoryCustom.findInactiveDeployments) — no per-deployment fan-out and
            // no materializing every participant group. Requires the recruitment normalized store to be
            // populated. `offset` is a page index, so the row offset is offset * limit (legacy contract).
            val paging = offset >= 0 && limit > 0
            recruitmentRepository
                .findInactiveDeployments(
                    studyId = studyId.stringRepresentation,
                    threshold = threshold.toJavaInstant(),
                    offset = if (paging) offset * limit else null,
                    limit = if (paging) limit else null,
                )
                .map { InactiveDeploymentInfo(UUID(it.deploymentId), it.lastDataUpload.toKotlinInstant()) }
        }

    override suspend fun getParticipantGroupsStatus(
        studyId: UUID,
        page: Int?,
        size: Int?,
        search: String?,
        status: String?,
    ): ParticipantGroupsStatus =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val allStatuses = core.getParticipantGroupStatusList(studyId)

            // Filtering/searching run against the core status list (deployment state + account identity
            // from the recruitment snapshot), so deciding which rows match needs no Keycloak or
            // data-stream calls.
            var matched = allStatuses.filterIsInstance<ParticipantGroupStatus.InDeployment>()
            if (!status.isNullOrBlank()) {
                matched = matched.filter { it.studyDeploymentStatus.matchesStateName(status) }
            }
            if (!search.isNullOrBlank()) {
                val needle = search.lowercase()
                matched = matched.filter { it.matchesSearch(needle) }
            }

            val paginating = page != null && size != null
            // A completely unfiltered, unpaged call is the legacy contract that returns everything.
            val isLegacyUnfiltered =
                !paginating && search.isNullOrBlank() && status.isNullOrBlank()
            val total = matched.size

            // Enrich ONLY the requested page — the expensive per-group account resolution and
            // last-upload lookup become O(page) instead of O(all). Offset is computed as Long so a
            // large page * size can't overflow Int and wrap back to an earlier page; an offset past
            // the end yields an empty page.
            val pageOfGroups =
                if (paginating) {
                    val offset = page.toLong() * size.toLong()
                    if (offset >= matched.size) emptyList() else matched.drop(offset.toInt()).take(size)
                } else {
                    matched
                }
            val lastUploadByDeployment = mutableMapOf<UUID, Instant?>()
            val participantGroupInfoList = pageOfGroups.map { enrichGroup(it, lastUploadByDeployment) }

            ParticipantGroupsStatus(
                groups = participantGroupInfoList,
                // Only the legacy unfiltered call keeps the full status list; as soon as the result is
                // filtered/searched/paged, `groupStatuses` must mirror `groups` so the two collections
                // never disagree (e.g. clients reading representation names off `groupStatuses`).
                groupStatuses = if (isLegacyUnfiltered) allStatuses else pageOfGroups,
                total = if (paginating) total else null,
            )
        }

    override suspend fun getParticipantGroupStatusCounts(studyId: UUID): DeploymentStatusCountsDto =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val statuses = core.getParticipantGroupStatusList(studyId)
            var invited = 0
            var deployingDevices = 0
            var running = 0
            var stopped = 0
            statuses.filterIsInstance<ParticipantGroupStatus.InDeployment>().forEach { group ->
                when (group.studyDeploymentStatus) {
                    is StudyDeploymentStatus.Invited -> invited++
                    is StudyDeploymentStatus.DeployingDevices -> deployingDevices++
                    is StudyDeploymentStatus.Running -> running++
                    is StudyDeploymentStatus.Stopped -> stopped++
                }
            }
            DeploymentStatusCountsDto(invited, deployingDevices, running, stopped, total = statuses.size)
        }

    /**
     * Matches a deployment state against a status-name filter (e.g. "Running"). Uses type checks
     * rather than reflecting on the class name so it is robust to minification/obfuscation.
     */
    private fun StudyDeploymentStatus.matchesStateName(name: String): Boolean =
        when (this) {
            is StudyDeploymentStatus.Invited -> name == "Invited"
            is StudyDeploymentStatus.DeployingDevices -> name == "DeployingDevices"
            is StudyDeploymentStatus.Running -> name == "Running"
            is StudyDeploymentStatus.Stopped -> name == "Stopped"
            else -> false
        }

    /** Matches a group against a lowercased search needle by deployment id or participant identity. */
    private fun ParticipantGroupStatus.InDeployment.matchesSearch(needle: String): Boolean {
        if (id.stringRepresentation.lowercase().contains(needle)) return true
        if (studyDeploymentStatus.studyDeploymentId.stringRepresentation.lowercase().contains(needle)) {
            return true
        }
        return participants.any { participant ->
            when (val identity = participant.accountIdentity) {
                is EmailAccountIdentity -> identity.emailAddress.address.lowercase().contains(needle)
                is UsernameAccountIdentity -> identity.username.name.lowercase().contains(needle)
                else -> false
            }
        }
    }

    /**
     * Enriches a single in-deployment group with participant account data and the deployment's last
     * upload time. Kept separate so callers can enrich just a page of groups rather than all of them.
     */
    private suspend fun enrichGroup(
        groupStatus: ParticipantGroupStatus.InDeployment,
        lastUploadByDeployment: MutableMap<UUID, Instant?>,
    ): ParticipantGroupInfo {
        val accountsByParticipant =
            groupStatus.participants.associateWith { participant ->
                // Generated (anonymous) accounts are username-only with no name/email to enrich, and
                // they always exist, so we resolve them locally instead of making a Keycloak call per
                // participant.
                // TODO(account-existence): local resolution ASSUMES the generated account exists rather
                //  than verifying it. See project_account_lookup_n1 for the long-term fix (per-study
                //  Keycloak group or a local participant read model).
                when (participant.accountIdentity) {
                    is UsernameAccountIdentity ->
                        Account.fromAccountIdentity(participant.accountIdentity)
                            .apply { role = Role.PARTICIPANT }
                    else -> accountService.findByAccountIdentity(participant.accountIdentity)
                }
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

        return ParticipantGroupInfo(groupStatus.id, groupStatus.studyDeploymentStatus, participantAccounts)
    }

    override suspend fun stopParticipantGroup(
        studyId: UUID,
        groupId: UUID,
    ): ParticipantGroupStatus =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            // Enforces ManageStudy/LimitedManageStudy on studyId (same claim as core's StopParticipantGroup)
            // and returns only groups belonging to this study — the correct membership check core botched.
            val groups = core.getParticipantGroupStatusList(studyId)
            require(groups.any { it.id == groupId }) {
                "Participant group with ID \"$groupId\" does not belong to study with ID \"$studyId\"."
            }

            // Stop the deployment directly, bypassing core's buggy require(recruitment.id == studyId).
            // deploymentCore.Stop requires InDeployment(groupId), which a study manager holds via the
            // ManageStudy -> InDeployment claim expansion in AuthenticationServiceImpl.getClaims.
            deploymentCore.stop(groupId)

            core.getParticipantGroupStatusList(studyId).first { it.id == groupId }
        }
}
