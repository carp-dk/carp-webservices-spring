package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.ApplicationData
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.studies.domain.StudyRepository
import dk.cachet.carp.studies.domain.StudySnapshot
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.consent.repository.ConsentDocumentRepository
import dk.cachet.carp.webservices.dataPoint.repository.DataPointRepository
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import dk.cachet.carp.webservices.export.repository.ExportRepository
import dk.cachet.carp.webservices.file.service.FileService
import dk.cachet.carp.webservices.study.domain.Study
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import dk.cachet.carp.studies.domain.Study as CoreStudy

@Service
@Suppress("LongParameterList")
class CoreStudyRepository(
    private val studyRepository: dk.cachet.carp.webservices.study.repository.StudyRepository,
    private val participantRepository: CoreParticipantRepository,
    private val dataPointRepository: DataPointRepository,
    private val collectionRepository: CollectionRepository,
    private val consentDocumentRepository: ConsentDocumentRepository,
    private val documentRepository: DocumentRepository,
    private val exportRepository: ExportRepository,
    private val objectMapper: ObjectMapper,
    private val validationMessages: MessageBase,
    private val fileService: FileService,
    transactionManager: PlatformTransactionManager,
) : StudyRepository {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    // Programmatic, not @Transactional: remove()'s multi-table delete needs to be atomic, but every
    // method here is a suspend fun dispatched via withContext(Dispatchers.IO), and Spring's classic
    // @Transactional AOP proxy commits as soon as that dispatch suspends - before the dispatched body's DB
    // calls ever run (see CoreParticipantRepository for the same fix, in more detail). The class-level
    // @Transactional this used to carry (plus remove()'s own `rollbackFor = [Exception::class]`) never
    // actually provided that atomicity.
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override suspend fun add(study: CoreStudy) =
        withContext(Dispatchers.IO) {
            check(studyRepository.getByStudyId(study.id.stringRepresentation) == null) {
                LOGGER.warn("Study already exists, id: ${study.id.stringRepresentation}")
                validationMessages.get("study.core.add.exists", study.id.stringRepresentation)
            }

            val studyToSave = Study()

            val applicationJson =
                buildJsonObject {
                    put("studyId", JsonPrimitive(study.id.stringRepresentation))
                }.toString()

            study.invitation =
                StudyInvitation(
                    study.invitation.name,
                    study.invitation.description,
                    ApplicationData(applicationJson),
                )

            val snapshot = WS_JSON.encodeToString(StudySnapshot.serializer(), study.getSnapshot())
            studyToSave.snapshot = objectMapper.readTree(snapshot)
            studyRepository.save(studyToSave)

            LOGGER.info("Study saved, id: ${study.id.stringRepresentation}")
        }

    override suspend fun getById(studyId: UUID): CoreStudy? =
        withContext(Dispatchers.IO) {
            val study = studyRepository.getByStudyId(studyId.stringRepresentation)

            if (study == null) {
                LOGGER.info("Study is not found, id: ${studyId.stringRepresentation}")
                return@withContext null
            }

            convertStudySnapshotNodeToStudy(study.snapshot!!)
        }

    override suspend fun getForOwner(ownerId: UUID): List<CoreStudy> =
        withContext(Dispatchers.IO) {
            val studies = studyRepository.findAllByOwnerId(ownerId.stringRepresentation)
            studies.map { convertStudySnapshotNodeToStudy(it.snapshot!!) }.toList()
        }

    /**
     * TODO: This should only remove the study from the study repository.
     * TODO: All associated data should be deleted by subscribing to `StudyService.Event.StudyRemoved`
     */
    override suspend fun remove(studyId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            val idsToRemove = getDeploymentIdsOrThrow(studyId)
            val collectionIds = collectionRepository.getCollectionIdsByStudyId(studyId.stringRepresentation)

            // Grouped in one real transaction: a failure partway through must not leave some of these
            // deleted and others not. fileService.deleteAllByStudyId is deliberately NOT included here -
            // it also deletes from external file storage, which no database transaction can span, so it
            // only runs after the DB-only deletes below have committed successfully.
            transactionTemplate.executeWithoutResult {
                documentRepository.deleteAllByCollectionIds(collectionIds)
                collectionRepository.deleteAllByDeploymentIds(idsToRemove.map { it.stringRepresentation })
                consentDocumentRepository.deleteAllByDeploymentIds(idsToRemove.map { it.stringRepresentation })
                // TODO: remove once study deletion migrates to StudyRemoved event handler (#376)
                @Suppress("DEPRECATION")
                dataPointRepository.deleteAllByDeploymentIds(idsToRemove.map { it.stringRepresentation })
                exportRepository.deleteByStudyId(studyId.stringRepresentation)
                studyRepository.deleteByStudyId(studyId.stringRepresentation)
            }

            fileService.deleteAllByStudyId(studyId.stringRepresentation)

            LOGGER.info("Study with id ${studyId.stringRepresentation} and all associated data deleted.")

            true
        }

    override suspend fun update(study: CoreStudy) =
        withContext(Dispatchers.IO) {
            val existingStudy = getWSStudyById(study.id)

            val snapshot = WS_JSON.encodeToString(StudySnapshot.serializer(), study.getSnapshot())
            existingStudy.snapshot = objectMapper.readTree(snapshot)
            studyRepository.save(existingStudy)

            LOGGER.info("Study updated, id: ${study.id.stringRepresentation}")
        }

    fun findAllByStudyIds(studyIds: List<UUID>): List<CoreStudy> =
        studyRepository.findAllByStudyIds(studyIds.map { it.stringRepresentation })
            .map { convertStudySnapshotNodeToStudy(it.snapshot!!) }
            .toList()

    suspend fun getWSStudyById(id: UUID): Study =
        withContext(Dispatchers.IO) {
            val study = studyRepository.getByStudyId(id.stringRepresentation)

            checkNotNull(study) {
                LOGGER.warn("Study is not found, id: ${id.stringRepresentation}")
                validationMessages.get("study.core.study.not_found", id.stringRepresentation)
            }

            study
        }

    suspend fun getDeploymentIdsOrThrow(studyId: UUID): Set<UUID> {
        val recruitment = participantRepository.getRecruitment(studyId)
        return recruitment?.participantGroups?.keys ?: emptySet()
    }

    suspend fun getStudySnapshotById(id: UUID): StudySnapshot {
        val study = getWSStudyById(id)
        return WS_JSON.decodeFromString(StudySnapshot.serializer(), study.snapshot.toString())
    }

    fun convertStudySnapshotNodeToStudy(node: JsonNode): CoreStudy {
        val snapshot = WS_JSON.decodeFromString<StudySnapshot>(node.toString())
        return CoreStudy.fromSnapshot(snapshot)
    }
}
