package dk.cachet.carp.webservices.export.command.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.export.command.ExportCommand
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.file.util.FileUtil
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.domain.AnonymousParticipant
import dk.cachet.carp.webservices.study.domain.AnonymousParticipantRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.nio.file.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ExportAnonymousParticipants(
    entry: Export,
    private val payload: AnonymousParticipantRequest,
    private val services: CoreServiceContainer,
    private val accountService: AccountService,
    private val resourceExporter: ResourceExporterService,
    private val fileUtil: FileUtil,
) : ExportCommand(entry) {
    private val studyId = UUID(entry.studyId)

    companion object {
        const val MAX_AMOUNT = 5000
        const val CSV_HEADER = "username,study_deployment_id,access_link,expiry_date"
    }

    override fun canExecute(): Pair<Boolean, String> {
        val protocol =
            runBlocking(Dispatchers.IO + SecurityCoroutineContext()) {
                services.studyService.getStudyDetails(studyId).protocolSnapshot
            }

        val isLive =
            runBlocking(Dispatchers.IO + SecurityCoroutineContext()) {
                services.studyService.getStudyStatus(studyId).canDeployToParticipants
            }

        return when {
            protocol == null ->
                Pair(
                    false,
                    "Study $studyId does not have a protocol",
                )
            !(protocol.participantRoles.any { it.role == payload.participantRoleName }) ->
                Pair(
                    false,
                    "Participant role ${payload.participantRoleName} does not exist",
                )
            !isLive ->
                Pair(
                    false,
                    "Study $studyId is not live",
                )
            payload.amountOfAccounts !in 1..MAX_AMOUNT ->
                Pair(
                    false,
                    "Amount of accounts must be between 1 and $MAX_AMOUNT",
                )
            else -> Pair(true, "")
        }
    }

    override suspend fun execute() {
        logger.info("Generating ${payload.amountOfAccounts} anonymous participants for study $studyId")

        val anonymousParticipants = mutableSetOf<AnonymousParticipant>()

        repeat(payload.amountOfAccounts) {
            val (identity, link) =
                accountService.generateAnonymousAccount(
                    payload.expirationSeconds,
                    payload.clientId,
                    payload.redirectUri,
                    payload.subdomain,
                )
            anonymousParticipants.add(createAnonymousParticipant(identity, link))
        }

        val csvBody =
            anonymousParticipants.map {
                "${it.username},${it.studyDeploymentId},\"${it.magicLink}\",${it.expiryDate}"
            }

        val csvPath =
            fileUtil.resolveFileStoragePathForFilenameAndRelativePath(
                entry.fileName,
                Path.of(entry.relativePath),
            )
        resourceExporter.exportCSV(CSV_HEADER, csvBody, csvPath, logger)
    }

    private suspend fun createAnonymousParticipant(
        identity: UsernameAccountIdentity,
        link: String,
    ): AnonymousParticipant {
        val participant = services.recruitmentService.addParticipant(studyId, identity.username)

        val groupStatus =
            services.recruitmentService.inviteNewParticipantGroup(
                studyId,
                setOf(
                    AssignedParticipantRoles(
                        participant.id,
                        AssignedTo.Roles(setOf(payload.participantRoleName)),
                    ),
                ),
            ) as ParticipantGroupStatus.InDeployment

        val deploymentId = groupStatus.studyDeploymentStatus.studyDeploymentId

        return AnonymousParticipant(
            UUID.parse(identity.username.name),
            deploymentId,
            link,
            Clock.System.now() + payload.expirationSeconds.toDuration(DurationUnit.SECONDS),
        )
    }
}
