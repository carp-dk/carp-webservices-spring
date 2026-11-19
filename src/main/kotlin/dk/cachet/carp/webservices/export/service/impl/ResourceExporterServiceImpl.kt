package dk.cachet.carp.webservices.export.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.domain.DeploymentRepository
import dk.cachet.carp.webservices.datastream.service.impl.DataStreamService
import dk.cachet.carp.webservices.export.domain.ExportLog
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.study.repository.CoreStudyRepository
import kotlinx.coroutines.CancellationException
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

@Service
@Suppress("TooGenericExceptionCaught")
class ResourceExporterServiceImpl(
    private val objectMapper: ObjectMapper,
    private val studyRepository: CoreStudyRepository,
    private val deploymentRepository: DeploymentRepository,
    beanFactory: ListableBeanFactory,
    private val dataStreamService: DataStreamService,
) : ResourceExporterService {
    private val exporters = beanFactory.getBeansOfType(ResourceExporter::class.java).values

    override suspend fun exportStudyData(
        studyId: UUID,
        deploymentIds: Set<UUID>?,
        targetDir: Path,
        log: ExportLog,
        activeDeploymentsOnly: Boolean?,
    ) {
        var studyDeploymentIds =
            deploymentIds
                ?: studyRepository.getDeploymentIdsOrThrow(studyId)

        if (activeDeploymentsOnly == true) {
            studyDeploymentIds =
                studyDeploymentIds.filter {
                    deploymentRepository.getStudyDeploymentBy(it)?.startedOn != null
                }.toSet()
        }

        exporters.forEach { exportToFile(it, studyId, studyDeploymentIds, targetDir, log) }

        dataStreamService.exportDataOrThrow(studyDeploymentIds, targetDir)

        exportLogAsFile(log, targetDir)
    }

    /**
     * Writes the [exporter]'s batches to its file one element at a time, so only the current batch is in memory.
     * The file is created only once there is data, and is removed if the export fails partway, so a failed export
     * never leaves a truncated file in the archive.
     */
    private suspend fun exportToFile(
        exporter: ResourceExporter<*>,
        studyId: UUID,
        deploymentIds: Set<UUID>,
        targetDir: Path,
        log: ExportLog,
    ) {
        val path = targetDir.resolve(exporter.dataFileName)
        var writer: JsonArrayFileWriter? = null

        try {
            exporter.exportBatchesOrThrow(studyId, deploymentIds, targetDir).collect { batch ->
                batch.forEach { element ->
                    val current = writer ?: openWriter(exporter, path).also { writer = it }
                    current.write(element)
                }
            }
            writer?.close()
        } catch (e: CancellationException) {
            writer?.abort()
            throw e
        } catch (e: Throwable) {
            writer?.abort()
            log.error("Failed to export data for: ${exporter.dataFileName}", e)
            return
        }

        if (writer == null) {
            log.info("No data found for: ${exporter.dataFileName}")
        } else {
            log.info("A new file is created for zipping with name ${path.fileName}.")
        }
    }

    private fun openWriter(
        exporter: ResourceExporter<*>,
        path: Path,
    ): JsonArrayFileWriter =
        exporter.exportSerializer
            ?.let { KotlinxJsonArrayFileWriter(path, it) }
            ?: JacksonJsonArrayFileWriter(path, objectMapper)

    override fun exportCSV(
        header: String,
        rows: List<String>,
        path: Path,
        log: ExportLog,
    ) {
        val csvContent =
            buildString {
                appendLine(header)
                rows.forEach { appendLine(it) }
            }

        try {
            Files.write(path, csvContent.encodeToByteArray())
        } catch (e: Throwable) {
            throw IllegalArgumentException("Failed to store CSV file ${path.fileName}.", e)
        }

        log.info("A new CSV file was created: ${path.fileName}")
    }

    private fun exportLogAsFile(
        log: ExportLog,
        rootFolder: Path,
    ) {
        val path = rootFolder.resolve("summary-logs.txt")

        try {
            val serializedResource = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(log)
            val resourceStream = ByteArrayInputStream(serializedResource.encodeToByteArray())
            Files.copy(resourceStream, path)

            log.info("A new log file is created for zipping with name ${path.fileName}.")
        } catch (e: Throwable) {
            log.error("Failed to store log file ${path.fileName}.", e)
        }
    }
}
