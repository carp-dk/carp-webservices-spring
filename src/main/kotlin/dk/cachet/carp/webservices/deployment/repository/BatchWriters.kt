package dk.cachet.carp.webservices.deployment.repository

import dk.cachet.carp.webservices.deployment.domain.ParticipantGroup as WSParticipantGroup
import dk.cachet.carp.webservices.deployment.domain.StudyDeployment as WSStudyDeployment

/**
 * Narrow, write-only view of [CoreDeploymentRepository] for callers that only ever need to batch-insert
 * deployments, not read/update/remove them - e.g. [dk.cachet.carp.webservices.study.service.impl.AnonymousServiceImp],
 * which runs partly on self-signup's unauthenticated public endpoint and has no other authorization check
 * for this dependency. Narrower than the concrete class means a future change in that class accidentally
 * calling `.remove(...)`/`.update(...)` from that unauthenticated path is a compile error, not a review miss.
 */
interface DeploymentBatchWriter {
    fun addAll(studyDeployments: List<WSStudyDeployment>)
}

/** Same rationale as [DeploymentBatchWriter], for [CoreParticipationRepository]. */
interface ParticipationBatchWriter {
    fun addAll(groups: List<WSParticipantGroup>)
}
