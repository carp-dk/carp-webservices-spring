package dk.cachet.carp.webservices.export.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.domain.DeploymentRepository
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.service.impl.DataStreamService
import dk.cachet.carp.webservices.export.domain.ExportLog
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.study.repository.CoreStudyRepository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.encodeToStream
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
            val serializer = it.exportSerializer
            if (serializer == null) {
                writeResourceAsJson(path, exports, log)
            } else {
                writeCoreResourceAsJson(path, serializer, exports, log)
            }
        }

        dataStreamService.exportDataOrThrow(studyDeploymentIds, targetDir)

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

    /**
     * Writes CARP Core types with [WS_JSON], the same serializer used to store them and to serve the API, streaming
     * straight to the file. Jackson cannot reproduce their format (polymorphic `__type`, `Instant`, `ApplicationData`).
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun writeCoreResourceAsJson(
        path: Path,
        serializer: KSerializer<*>,
        resources: Collection<*>,
        log: ExportLog,
    ) {
        @Suppress("UNCHECKED_CAST")
        val listSerializer = ListSerializer(serializer as KSerializer<Any?>)

        try {
            Files.newOutputStream(path).buffered().use {
                WS_JSON.encodeToStream(listSerializer, resources.toList(), it)
            }

            log.info("A new file is created for zipping with name ${path.fileName}.")
        } catch (e: Throwable) {
            log.error("An error occurred while storing the file ${path.fileName}", e)
        }
    }

    /** Writes web-services domain types (Jackson-annotated entities) with Jackson. */
    private fun writeResourceAsJson(
        path: Path,
        resource: Any,
        log: ExportLog,
    ) {
        try {
            val jsonGenerator = objectMapper.createGenerator(path.toFile().outputStream())

            jsonGenerator.use { objectMapper.writeValue(it, resource) }

            log.info("A new file is created for zipping with name ${path.fileName}.")
        } catch (e: Throwable) {
            log.error("An error occurred while storing the file ${path.fileName}", e)
        }
    }
}
