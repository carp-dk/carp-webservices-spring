package dk.cachet.carp.webservices.file.repository

import dk.cachet.carp.webservices.file.domain.File
import org.springframework.context.annotation.Lazy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.JsonNode
import java.time.Instant

@Repository
interface FileRepository : JpaRepository<File, Int>, JpaSpecificationExecutor<File>, FileRepositoryCustom {
    fun findByStudyIdAndCreatedBy(
        studyId: String,
        createdBy: String,
    ): List<File>

    fun findByStudyId(studyId: String): List<File>

    fun findByStudyIdAndOriginalName(
        studyId: String,
        originalName: String,
    ): List<File>

    fun findByStudyIdAndOriginalNameAndCreatedBy(
        studyId: String,
        originalName: String,
        createdBy: String,
    ): List<File>

    fun getAllByUpdatedAtIsBefore(updatedAtBefore: Instant): MutableList<File>
}

// TODO: This is not a repository, dont't be mislead by that, it needs to be moved to its own service somewhere else.
interface FileRepositoryCustom {
    /**
     * The [save] interface inserters the files in the filesystem.
     *
     * @param studyId The [studyId] of the study.
     * @param uploadedFile The [uploadedFile] in a multipart request.
     * @param fileName The [fileName] of the file.
     * @param metadata The [metadata] of the file.
     * @param ownerId The [ownerId] of the file.
     * @param deploymentId The [deploymentId] of the file.
     * @param relativePath The [relativePath] of the file.
     */
    @Suppress("LongParameterList")
    fun save(
        studyId: String,
        uploadedFile: MultipartFile,
        fileName: String,
        metadata: JsonNode?,
        ownerId: String?,
        deploymentId: String?,
        relativePath: String,
    ): File
}

@Repository
class FileRepositoryImpl(
    @Lazy private val fileRepository: FileRepository,
) : FileRepositoryCustom {
    /**
     * The [save] function saves the files to the filesystem.
     *
     * @param studyId The [studyId] of the study to save the file.
     * @param uploadedFile The [uploadedFile] to be saved to the filesystem.
     * @param fileName The [fileName] of the file.
     * @param metadata The [metadata] of the file.
     * @param ownerId The [ownerId] of the file.
     * @param deploymentId The [deploymentId] of the file.
     * @param relativePath The [relativePath] of the file.
     */
    override fun save(
        studyId: String,
        uploadedFile: MultipartFile,
        fileName: String,
        metadata: JsonNode?,
        ownerId: String?,
        deploymentId: String?,
        relativePath: String,
    ): File {
        val file =
            File(
                fileName = fileName,
                originalName = uploadedFile.originalFilename!!,
                metadata = metadata,
                studyId = studyId,
                ownerId = ownerId,
                deploymentId = deploymentId,
                relativePath = relativePath,
            )
        return fileRepository.save(file)
    }
}
