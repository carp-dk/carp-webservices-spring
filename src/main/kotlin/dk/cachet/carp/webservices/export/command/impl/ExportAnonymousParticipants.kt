package dk.cachet.carp.webservices.export.command.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.UUIDRegex
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
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import dk.cachet.carp.webservices.study.service.AnonymousService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinDuration

/**
 * Generates anonymous participants for a study and writes their magic links to a CSV export.
 *
 * NOT idempotent by design. Accounts and participants are created in per-batch transactions (see
 * [AnonymousService.bulkAddParticipantsAndGroups]) so a batch is atomic, but the export as a whole is not:
 * if a run fails part-way, the batches that already committed remain as orphaned Keycloak accounts and
 * recruitment rows, and a re-submitted export mints a fresh set rather than resuming or de-duplicating.
 * This is an accepted trade-off — a whole-export transaction / buffered CSV is infeasible at the 5M ceiling,
 * and mid-run failures are not expected. A failed run's orphans must be cleaned up manually.
 */
@Suppress("LongParameterList")
class ExportAnonymousParticipants(
    entry: Export,
    private val payload: AnonymousParticipantRequest,
    private val services: CoreServiceContainer,
    private val anonymousService: AnonymousService,
    private val accountService: AccountService,
    private val resourceExporter: ResourceExporterService,
    private val fileUtil: FileUtil,
) : ExportCommand(entry) {
    private val studyId = UUID(entry.studyId)

    companion object {
        const val MAX_AMOUNT = 5000000
        const val CSV_HEADER = "username,study_deployment_id,access_link,expiry_date"
        val LOGGER: Logger? = LogManager.getLogger()
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

        if (payload.useFastPipeline) {
            fastPipeline()
        } else {
            oldPipeline()
        }
    }

    private suspend fun oldPipeline() {
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

    @Suppress("MagicNumber", "MaxLineLength")
    private suspend fun fastPipeline() {
        val csvPath =
            fileUtil.resolveFileStoragePathForFilenameAndRelativePath(
                entry.fileName,
                Path.of(entry.relativePath),
            )

        val users = mutableListOf<Pair<String, String>>()
        var received = 0L
        var skipped = 0L
        var written = 0L

        csvPath.toFile().bufferedWriter().use { writer ->
            writer.write(CSV_HEADER)
            writer.newLine()

            // Flush the buffered accounts into participants + CSV rows, returning the number written.
            suspend fun flush() {
                if (users.isEmpty()) return
                createAnonymousParticipant(users).forEach { participant ->
                    val row =
                        "${participant.username},${participant.studyDeploymentId}," +
                            "\"${participant.magicLink}\",${participant.expiryDate}"
                    writer.write(row)
                    writer.newLine()
                }
                written += users.size
                users.clear()
            }

            accountService.generateAnonymousAccountBulk(
                payload.expirationSeconds,
                payload.clientId,
                payload.redirectUri,
                payload.subdomain,
                payload.amountOfAccounts,
                studyId.stringRepresentation,
            ).collect { response ->
                if (++received % 1000L == 0L) {
                    LOGGER?.info("Received $received/${payload.amountOfAccounts} anonymous accounts")
                }
                val userId = response.userId
                val link = response.link
                // Skip malformed responses rather than aborting the whole export: a null field, or a
                // userId that is not a UUID (buildParticipants parses it with UUID(...), which would
                // otherwise throw and fail the entire batch).
                if (userId == null || link == null) {
                    skipped++
                    LOGGER?.warn(
                        "Skipping anonymous account with missing field (userId=$userId, hasLink=${link != null})",
                    )
                    return@collect
                }
                if (!UUIDRegex.matches(userId)) {
                    skipped++
                    LOGGER?.warn("Skipping anonymous account with non-UUID userId: $userId")
                    return@collect
                }
                users.add(userId to link)
                if (users.size >= 1000) flush()
            }
            flush()

            if (skipped > 0L || written < payload.amountOfAccounts.toLong()) {
                LOGGER?.warn(
                    "Anonymous participant export for study $studyId under-delivered: " +
                        "requested=${payload.amountOfAccounts}, received=$received, written=$written, skipped=$skipped",
                )
            } else {
                LOGGER?.info("Anonymous participant export for study $studyId wrote $written participants")
            }
        }

        // Schedule these accounts for later cleanup (see AnonymousService). Keyed on `received`, not
        // `written`: skipped/malformed accounts still exist in the study's Keycloak group and must be
        // swept too, so a generation that produced only unusable accounts must still schedule cleanup.
        // delete_after = link expiry + CLEANUP_BUFFER so cleanup never races expiry / clock skew / a late
        // redemption; the study's timer is reset/extended to the latest generation on each call.
        if (received > 0L) {
            val deleteAfter =
                Clock.System.now() +
                    payload.expirationSeconds.toDuration(DurationUnit.SECONDS) +
                    AnonymousAccountCleanupStore.CLEANUP_BUFFER.toKotlinDuration()
            anonymousService.recordCleanupSchedule(studyId, deleteAfter.toJavaInstant(), received)
        }
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

    private suspend fun createAnonymousParticipant(pairs: List<Pair<String, String>>): List<AnonymousParticipant> {
        val groups =
            anonymousService.bulkAddParticipantsAndGroups(
                studyId,
                payload.participantRoleName,
                pairs,
            )

        val anonymousParticipants =
            groups.map { group ->
                AnonymousParticipant(
                    group.participantIds.single(),
                    group.id,
                    pairs.first { it.first == group.participantIds.single().toString() }.second,
                    Clock.System.now() + payload.expirationSeconds.toDuration(DurationUnit.SECONDS),
                )
            }

        return anonymousParticipants
    }
}
