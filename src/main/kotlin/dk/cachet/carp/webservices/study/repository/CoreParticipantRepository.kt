package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.domain.users.ParticipantRepository
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.study.domain.Recruitment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import dk.cachet.carp.studies.domain.users.Recruitment as CoreRecruitment

@Service
@Transactional
class CoreParticipantRepository(
    private val recruitmentRepository: RecruitmentRepository,
) : ParticipantRepository {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun addRecruitment(recruitment: CoreRecruitment) =
        withContext(Dispatchers.IO) {
            val studyId = recruitment.studyId.stringRepresentation
            val existingRecruitment = recruitmentRepository.findRecruitmentByStudyId(studyId)

            check(existingRecruitment == null) { "A recruitment already exists for the study with id $studyId." }

            val snapshotJsonNode = WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), recruitment.getSnapshot())
            val newRecruitment =
                Recruitment().apply {
                    snapshot = snapshotJsonNode
                }
            val saved = recruitmentRepository.save(newRecruitment)
            LOGGER.info("New recruitment with id ${saved.id} is saved for study with id $studyId.")
        }

    override suspend fun getRecruitment(studyId: UUID): CoreRecruitment? =
        withContext(Dispatchers.IO) {
            val existingRecruitment = recruitmentRepository.findRecruitmentByStudyId(studyId.stringRepresentation)

            if (existingRecruitment == null) {
                LOGGER.info("Recruitment for studyId $studyId is not found.")
                return@withContext null
            }

            mapWSRecruitmentToCore(existingRecruitment)
        }

    override suspend fun getRecruitmentWithParticipantGroup(groupId: UUID): CoreRecruitment? =
        withContext(Dispatchers.IO) {
            recruitmentRepository.findRecruitmentByParticipantGroupId(groupId.stringRepresentation)
                ?.let(::mapWSRecruitmentToCore)
        }

    /**
     * Resolves the study a deployment belongs to. Backed by an indexed lookup that does not load the
     * recruitment snapshot, so it is cheap enough to call on the authorization hot path.
     */
    fun getStudyIdByDeploymentId(deploymentId: UUID): UUID? =
        recruitmentRepository.findStudyIdByDeploymentId(deploymentId.stringRepresentation)
            ?.let { UUID(it) }

    override suspend fun removeStudy(studyId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            getRecruitment(studyId) ?: return@withContext false
            recruitmentRepository.deleteByStudyId(studyId.stringRepresentation)
            LOGGER.info("Recruitment with studyId ${studyId.stringRepresentation} is deleted.")
            true
        }

    override suspend fun updateRecruitment(recruitment: CoreRecruitment) =
        withContext(Dispatchers.IO) {
            val studyId = recruitment.studyId.stringRepresentation
            val recruitmentFound =
                recruitmentRepository.findRecruitmentByStudyId(studyId)
                    ?: throw ResourceNotFoundException("Recruitment with studyId $studyId is not found.")

            val newSnapshot = WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), recruitment.getSnapshot())
            recruitmentFound.snapshot = newSnapshot
            recruitmentRepository.save(recruitmentFound)
            LOGGER.info("Recruitment with studyId $studyId is updated.")
        }

    private fun mapWSRecruitmentToCore(recruitment: Recruitment): CoreRecruitment {
        val snapshot = WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), recruitment.snapshot!!)
        return CoreRecruitment.fromSnapshot(snapshot)
    }
}
