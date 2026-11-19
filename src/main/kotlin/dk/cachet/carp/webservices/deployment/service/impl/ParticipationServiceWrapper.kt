package dk.cachet.carp.webservices.deployment.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.users.ParticipantData
import dk.cachet.carp.deployments.domain.users.ParticipantGroup
import dk.cachet.carp.deployments.domain.users.ParticipationRepository
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.deployment.service.ParticipationService
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.export.service.ResourceExporter.Companion.EXPORT_BATCH_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class ParticipationServiceWrapper(
    private val repository: ParticipationRepository,
    services: CoreServiceContainer,
) : ParticipationService, ResourceExporter<ParticipantData> {
    final override val core = services.participationService

    override suspend fun getParticipantGroup(studyDeploymentId: UUID): ParticipantGroup? {
        return repository.getParticipantGroup(studyDeploymentId)
    }

    final override val dataFileName = "participant-data.json"

    final override val exportSerializer = ParticipantData.serializer()

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = exportBatchesOrThrow(studyId, deploymentIds, target).toList().flatten()

    /**
     * Loads the participant groups [EXPORT_BATCH_SIZE] at a time, in [deploymentIds] order.
     * Like the single-group lookup this replaces, fails when a deployment has no participant group.
     */
    override fun exportBatchesOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ): Flow<List<ParticipantData>> =
        deploymentIds
            .chunked(EXPORT_BATCH_SIZE)
            .asFlow()
            .map { batch ->
                val groups = repository.getParticipantGroupList(batch.toSet()).associateBy { it.studyDeploymentId }
                batch.map { id ->
                    val group = requireNotNull(groups[id]) { "No participant group found for deployment $id." }
                    ParticipantData(group.studyDeploymentId, group.commonData, group.roleData)
                }
            }
}
