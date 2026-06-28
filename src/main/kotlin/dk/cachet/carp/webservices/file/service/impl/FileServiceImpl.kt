package dk.cachet.carp.webservices.file.service.impl

import cz.jirutka.rsql.parser.RSQLParser
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.query.QueryVisitor
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.file.domain.File
import dk.cachet.carp.webservices.file.repository.FileRepository
import dk.cachet.carp.webservices.file.service.FileService
import dk.cachet.carp.webservices.file.service.FileStorage
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

@Service
@Transactional
// Extract S3 methods into a separate service
@Suppress("LongParameterList", "TooManyFunctions")
class FileServiceImpl(
    private val fileRepository: FileRepository,
    private val fileStorage: FileStorage,
    private val validateMessages: MessageBase,
    private val s3Client: S3Client,
    private val authenticationService: AuthenticationService,
    // no slashes in bucketname allowed
    @param:Value("\${s3.space.bucket}") private val s3SpaceBucket: String,
    @param:Value("\${s3.space.endpoint}") private val s3SpaceEndpoint: String,
) : FileService, ResourceExporter<File> {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun getAll(
        query: String?,
        studyId: String,
    ): List<File> {
        val id = authenticationService.getId()
        val role = authenticationService.getRole()
        val isFullyAuthorized = role >= Role.RESEARCH_ASSISTANT

        if (isFullyAuthorized && query == null) {
            return fileRepository.findByStudyId(studyId)
        } else {
            query?.let {
                val queryForRole =
                    if (!isFullyAuthorized) {
                        // Return data relevant to this user only.
                        "$query;created_by==$id;study_id==$studyId"
                    } else {
                        // Return data relevant to this study.
                        "$query;study_id==$studyId"
                    }
                val specification = RSQLParser().parse(queryForRole).accept(QueryVisitor<File>())
                return fileRepository.findAll(specification)
            }
            return fileRepository.findByStudyIdAndCreatedBy(studyId, id.stringRepresentation)
        }
    }

    override fun getOne(id: Int): File {
        val optionalFile = fileRepository.findById(id)
        if (!optionalFile.isPresent) {
            LOGGER.warn("File is not found with id = $id")
            throw ResourceNotFoundException(validateMessages.get("file.not_found", id))
        }
        return optionalFile.get()
    }

    override fun create(
        studyId: UUID,
        deploymentId: UUID,
        ownerId: UUID,
        file: MultipartFile,
        metadata: String?,
    ): File {
        val relativePath =
            Path.of("studies", studyId.stringRepresentation, "deployments", deploymentId.stringRepresentation)
        val filename = fileStorage.storeAtPath(file, relativePath)

        val saved =
            fileRepository.save(
                studyId = studyId.stringRepresentation,
                uploadedFile = file,
                fileName = filename,
                metadata = metadata?.let { json -> ObjectMapper().readTree(json) },
                ownerId = ownerId.stringRepresentation,
                deploymentId = deploymentId.stringRepresentation,
                relativePath = relativePath.toString(),
            )

        LOGGER.info("File saved, id = ${saved.id}")

        return saved
    }

    override fun download(
        id: Int,
        studyId: UUID,
    ): Pair<Resource, String> {
        val file = getOne(id)

        val fileToDownload =
            fileStorage.getFileAtPath(
                file.fileName,
                Path.of(file.relativePath),
            )

        return Pair(fileToDownload, file.originalName)
    }

    override fun delete(
        id: Int,
        studyId: UUID,
    ) {
        val file = getOne(id)

        fileStorage.deleteFileAtPath(file.fileName, Path.of(file.relativePath))
        fileRepository.delete(file)

        LOGGER.info("File deleted, id = $id")
    }

    override suspend fun deleteAllByStudyId(studyId: String) {
        val files =
            withContext(Dispatchers.IO) {
                fileRepository.findByStudyId(studyId)
            }

        files.forEach { fileRepository.deleteById(it.id) }
        files.forEach { fileStorage.deleteFileAtPath(it.fileName, Path.of(it.relativePath)) }

        LOGGER.info("All files deleted for study, studyId = $studyId")
    }

    /**
     * The function [uploadImage] uploads the image to the configured S3 space.
     * TODO: functions for handling images are compromises due to time limitations.
     *       In an ideal scenario there would be an imageService and controller
     *       handling these kinds of requests. S3 storage is more sophisticated
     *       so it would make sense to handle not only the images, but every file
     *       as s3 objects. It would require to rewrite endpoints that deal with
     *       files though. If one is worried about privacy, there are self-hosted
     *       s3 solutions, which only need to be added to the docker-compose stack
     *
     * @param file The [file] is the file that needs to be uploaded
     * @return The url where you can access the content
     */
    override fun uploadImage(
        file: MultipartFile,
        studyId: String,
    ): String {
        val fileExtension = StringUtils.getFilenameExtension(file.originalFilename)
        val key = "studies/$studyId/${UUID.randomUUID().stringRepresentation}.$fileExtension"
        val fileMetadata = mutableMapOf<String, String>()

        fileMetadata["Content-Length"] = file.size.toString()

        if (!file.contentType.isNullOrEmpty()) {
            fileMetadata["Content-Type"] = file.contentType!!
        }

        val request =
            PutObjectRequest.builder()
                .bucket(s3SpaceBucket)
                .key(key)
                .metadata(fileMetadata)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .ifNoneMatch("*")
                .build()

        s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))

        return "https://$s3SpaceBucket.${s3SpaceEndpoint.removePrefix("https://")}/$key"
    }

    override fun deleteImage(url: String) {
        val key = url.substringAfter("${s3SpaceEndpoint.removePrefix("https://")}/", "")

        LOGGER.info("Deleting s3 resource with uri: $url")

        val deleteRequest =
            DeleteObjectRequest.builder()
                .bucket(s3SpaceBucket)
                .key(key)
                .build()

        s3Client.deleteObject(deleteRequest)
    }

    override val dataFileName = "files.json"

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = withContext(Dispatchers.IO) {
        getAll(null, studyId.stringRepresentation).onEach {
            val resource =
                fileStorage.getResourceAtPath(
                    it.fileName,
                    Path.of("studies", studyId.stringRepresentation, "deployments", it.deploymentId ?: "unknown"),
                )
            val baseName = it.originalName.substringBeforeLast(".")
            val extension = it.originalName.substringAfterLast(".", "")
            var counter = 1
            var copyPath = target.resolve(it.originalName)
            while (!Files.notExists(copyPath)) {
                copyPath = target.resolve("$baseName ($counter).$extension")
                counter++
            }
            Files.copy(resource.file.toPath(), copyPath)
        }
    }
}
