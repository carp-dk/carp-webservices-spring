package dk.cachet.carp.webservices.export.command

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.extensions.toSlug
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.export.command.impl.ExportAnonymousParticipants
import dk.cachet.carp.webservices.export.command.impl.ExportSummary
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.domain.ExportLog
import dk.cachet.carp.webservices.export.domain.ExportStatus
import dk.cachet.carp.webservices.export.domain.ExportType
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.file.util.FileUtil
import dk.cachet.carp.webservices.study.domain.AnonymousParticipantRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A command to encapsulate the logic for long-running exports.
 */
abstract class ExportCommand(
    open val entry: Export,
    open val logger: ExportLog = ExportLog(),
) {
    abstract fun canExecute(): Pair<Boolean, String>

    abstract suspend fun execute()
}

@Service
class ExportCommandFactory(
    private val services: CoreServiceContainer,
    private val accountService: AccountService,
    private val resourceExporter: ResourceExporterService,
    private val fileUtil: FileUtil,
) {
    companion object {
        const val INT_BUFFER_SIZE = 4
    }

    suspend fun createExportSummary(
        studyId: UUID,
        deploymentIds: Set<UUID>?,
    ): ExportCommand {
        // This hash will prohibit the creation of new exports for the same study and deployments within the same hour.
        // TODO: better rate limiting
        val id =
            createHash(
                studyId.stringRepresentation,
                deploymentIds?.joinToString(separator = "") ?: "",
                Clock.System.now().toJavaInstant().truncatedTo(ChronoUnit.SECONDS).toString(),
            )

        val exportType =
            if (deploymentIds.isNullOrEmpty()) {
                ExportType.STUDY_DATA
            } else {
                ExportType.DEPLOYMENT_DATA
            }

        val entry =
            Export(
                id = id.toString(),
                fileName = getDefaultFileName(studyId, exportType, "zip"),
                studyId = studyId.toString(),
                status = ExportStatus.IN_PROGRESS,
                type = exportType,
            )

        return ExportSummary(entry, deploymentIds, resourceExporter, fileUtil)
    }

    suspend fun createExportAnonymousParticipants(
        studyId: UUID,
        payload: AnonymousParticipantRequest,
    ): ExportCommand {
        val id =
            createHash(
                studyId.stringRepresentation,
                Clock.System.now().toJavaInstant().truncatedTo(ChronoUnit.SECONDS).toString(),
            )

        val entry =
            Export(
                id = id.toString(),
                fileName = getDefaultFileName(studyId, ExportType.ANONYMOUS_PARTICIPANTS, "csv"),
                studyId = studyId.toString(),
                status = ExportStatus.IN_PROGRESS,
                type = ExportType.ANONYMOUS_PARTICIPANTS,
            )

        return ExportAnonymousParticipants(
            entry,
            payload,
            services,
            accountService,
            resourceExporter,
            fileUtil,
        )
    }

    private suspend fun getDefaultFileName(
        studyId: UUID,
        type: ExportType,
        extension: String,
    ): String {
        val studyName = services.studyService.getStudyDetails(studyId).name.toSlug()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        val now = LocalDateTime.now().format(formatter)

        return "${now}_${type.toString().toSlug()}_$studyName.$extension"
    }

    /**
     * Create a hash based on the given components. The hash is used to uniquely identify an export.
     */
    private fun createHash(vararg components: String): UUID {
        val hashCode = components.joinToString(separator = "").hashCode()
        val hashBytes = ByteBuffer.allocate(INT_BUFFER_SIZE).putInt(hashCode).array()

        return UUID(java.util.UUID.nameUUIDFromBytes(hashBytes).toString())
    }
}
