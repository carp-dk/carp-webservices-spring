package dk.cachet.carp.webservices.deployment.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.deployment.repository.StudyDeploymentRepository
import dk.cachet.carp.webservices.deployment.service.DeploymentService
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.export.service.ResourceExporter.Companion.EXPORT_BATCH_SIZE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class DeploymentServiceWrapper(
    private val repository: StudyDeploymentRepository,
    services: CoreServiceContainer,
) : DeploymentService, ResourceExporter<StudyDeploymentSnapshot> {
    final override val core = services.deploymentService

    final override val dataFileName = "deployments.json"

    final override val exportSerializer = StudyDeploymentSnapshot.serializer()

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = exportBatchesOrThrow(studyId, deploymentIds, target).toList().flatten()

    /** Loads the deployments [EXPORT_BATCH_SIZE] at a time; each snapshot embeds the full protocol. */
    override fun exportBatchesOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ): Flow<List<StudyDeploymentSnapshot>> =
        deploymentIds
            .chunked(EXPORT_BATCH_SIZE)
            .asFlow()
            .map { batch ->
                repository
                    .findAllByStudyDeploymentIds(batch.map { it.stringRepresentation })
                    .map { WS_JSON.decodeFromString(StudyDeploymentSnapshot.serializer(), it.snapshot!!.toString()) }
            }.flowOn(Dispatchers.IO)
}
