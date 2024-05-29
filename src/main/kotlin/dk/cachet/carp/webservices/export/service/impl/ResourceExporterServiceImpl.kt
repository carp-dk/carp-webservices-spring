package dk.cachet.carp.webservices.export.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.export.domain.ExportLog
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.study.repository.CoreStudyRepository
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

@Service
@Suppress("TooGenericExceptionCaught")
class ResourceExporterServiceImpl(
    private val objectMapper: ObjectMapper,
    private val studyRepository: CoreStudyRepository,
    beanFactory: ListableBeanFactory,
) : ResourceExporterService {
    private val exporters = beanFactory.getBeansOfType(ResourceExporter::class.java).values

    override suspend fun exportStudyData(
        studyId: UUID,
        deploymentIds: Set<UUID>?,
        targetDir: Path,
        log: ExportLog,
    ) {
        val studyDeploymentIds = deploymentIds ?: studyRepository.getDeploymentIdsOrThrow(studyId)

        exporters.forEach {
            val exportResult = runCatching { it.exportDataOrThrow(studyId, studyDeploymentIds, targetDir) }

            if (exportResult.isFailure) {
                log.error("Failed to export data for: ${it.dataFileName}", exportResult.exceptionOrNull())
                return@forEach
            }

            val exports = exportResult.getOrNull()
            if (exports.isNullOrEmpty()) {
                log.info("No data found for: ${it.dataFileName}")
                return@forEach
            }

            val path = targetDir.resolve(it.dataFileName)
            writeResourceAsJson(path, exports, log)
        }

        exportLogAsFile(log, targetDir)
    }

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

    private fun writeResourceAsJson(
        path: Path,
        resource: Any,
        log: ExportLog,
    ) {
        try {
            val jsonGenerator = objectMapper.factory.createGenerator(path.toFile().outputStream())

            jsonGenerator.use { objectMapper.writeValue(it, resource) }

            log.info("A new file is created for zipping with name ${path.fileName}.")
        } catch (e: Throwable) {
            log.error("An error occurred while storing the file ${path.fileName}", e)
        }
    }
}
