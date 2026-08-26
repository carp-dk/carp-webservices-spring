package dk.cachet.carp.webservices.deployment.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.domain.DeploymentRepository
import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.deployment.domain.StudyDeployment
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.AuditorAware
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import dk.cachet.carp.deployments.domain.StudyDeployment as CoreStudyDeployment

@Service
@Transactional
class CoreDeploymentRepository(
    private val studyDeploymentRepository: StudyDeploymentRepository,
    private val objectMapper: ObjectMapper,
    private val validationMessages: MessageBase,
    private val auth: AuthorizationService,
    private val jdbcTemplate: JdbcTemplate,
    private val auditorAware: AuditorAware<String>,
) : DeploymentRepository, DeploymentBatchWriter {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun add(studyDeployment: CoreStudyDeployment) =
        withContext(Dispatchers.IO) {
            if (studyDeploymentRepository.findByDeploymentId(studyDeployment.id.stringRepresentation) != null) {
                LOGGER.warn("Deployment already exists, id: ${studyDeployment.id.stringRepresentation}")
                throw IllegalArgumentException(
                    validationMessages.get(
                        "deployment.add.study_deployment.exists",
                        studyDeployment.id.stringRepresentation,
                    ),
                )
            }
            val studyDeploymentToSave = StudyDeployment()

            val snapshot =
                WS_JSON.encodeToString(
                    StudyDeploymentSnapshot.serializer(),
                    studyDeployment.getSnapshot(),
                )
            studyDeploymentToSave.snapshot = objectMapper.readTree(snapshot)

            studyDeploymentRepository.save(studyDeploymentToSave)
            LOGGER.info("Deployment saved, id: ${studyDeployment.id.stringRepresentation}")
        }

    /**
     * Batch-insert [studyDeployments]. Not a suspend function: its sole caller (anonymous bulk generation)
     * invokes it inside a TransactionTemplate so the recruitment, deployment and participant-group writes
     * for one batch share a single transaction (propagation REQUIRED joins that transaction).
     */
    @Suppress("MagicNumber", "MaxLineLength")
    override fun addAll(studyDeployments: List<StudyDeployment>) {
        val timestamp = Timestamp.from(java.time.Instant.now())
        val auditor = auditorAware.currentAuditor.orElse("system")
        val sql =
            "INSERT INTO deployments (created_at, created_by, updated_at, updated_by, snapshot) " +
                "VALUES (?,?,?,?,?::jsonb)"
        jdbcTemplate.batchUpdate(sql, studyDeployments, studyDeployments.size) { ps, deployment ->
            ps.setObject(1, timestamp)
            ps.setObject(2, auditor)
            ps.setObject(3, timestamp)
            ps.setObject(4, auditor)
            ps.setObject(5, deployment.snapshot?.toString())
        }
    }

    override suspend fun getStudyDeploymentBy(id: UUID) =
        withContext(Dispatchers.IO) {
            val result = getWSDeploymentById(id) ?: return@withContext null
            val snapshot = WS_JSON.decodeFromString<StudyDeploymentSnapshot>(result.snapshot!!.toString())
            CoreStudyDeployment.fromSnapshot(snapshot)
        }

    override suspend fun getStudyDeploymentsBy(ids: Set<UUID>) =
        withContext(Dispatchers.IO) {
            val idStrings = ids.map { it.toString() }.toSet()
            studyDeploymentRepository.findAllByStudyDeploymentIds(idStrings).map { mapWSDeploymentToCore(it) }
        }

    override suspend fun remove(studyDeploymentIds: Set<UUID>): Set<UUID> =
        withContext(Dispatchers.IO) {
            val ids = studyDeploymentIds.map { it.stringRepresentation }.toSet()
            val idsPresent =
                studyDeploymentRepository.findAllByStudyDeploymentIds(ids)
                    .map { mapWSDeploymentToCore(it).id.stringRepresentation }
            studyDeploymentRepository.deleteByDeploymentIds(idsPresent)
            LOGGER.info("Deployments removed with ids: ${idsPresent.joinToString(", ")}")
            val idsPresentAsUUIDs = idsPresent.map { UUID(it) }.toSet()
            revokeStudyDeploymentClaims(studyDeploymentIds)

            idsPresentAsUUIDs
        }

    override suspend fun update(studyDeployment: CoreStudyDeployment) =
        withContext(Dispatchers.IO) {
            val deploymentId = studyDeployment.id
            val stored = getWSDeploymentById(deploymentId)

            checkNotNull(stored) {
                LOGGER.warn("Deployment is not found, id: ${deploymentId.stringRepresentation}")
                validationMessages.get(
                    "deployment.update.study_deployment.not_found",
                    deploymentId.stringRepresentation,
                )
            }

            val snapshot = WS_JSON.encodeToString(StudyDeploymentSnapshot.serializer(), studyDeployment.getSnapshot())
            stored.snapshot = objectMapper.readTree(snapshot)

            studyDeploymentRepository.save(stored)
            LOGGER.info("Deployment updated, id: ${studyDeployment.id.stringRepresentation}")
        }

    fun getWSDeploymentById(id: UUID): StudyDeployment? {
        val optionalResult = studyDeploymentRepository.findByDeploymentId(id.stringRepresentation)
        if (optionalResult == null) {
            LOGGER.info("Deployment is not found, id: ${id.stringRepresentation}")
            return null
        }
        return optionalResult
    }

    private fun mapWSDeploymentToCore(deployment: StudyDeployment): CoreStudyDeployment {
        val snapshot = WS_JSON.decodeFromString<StudyDeploymentSnapshot>(deployment.snapshot!!.toString())
        return CoreStudyDeployment.fromSnapshot(snapshot)
    }

    private suspend fun revokeStudyDeploymentClaims(ids: Set<UUID>) {
        val claims =
            ids.map {
                Claim.InDeployment(it)
            }.toSet()

        auth.revokeClaimsFromAllAccounts(claims)
    }
}
