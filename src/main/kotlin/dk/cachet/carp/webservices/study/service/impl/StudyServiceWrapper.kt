package dk.cachet.carp.webservices.study.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.studies.domain.StudySnapshot
import dk.cachet.carp.studies.infrastructure.StudyServiceRequest
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.protocol.service.ProtocolService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.domain.StudyOverview
import dk.cachet.carp.webservices.study.repository.CoreStudyRepository
import dk.cachet.carp.webservices.study.service.StudyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class StudyServiceWrapper(
    private val accountService: AccountService,
    private val studyRepository: CoreStudyRepository,
    private val protocolService: ProtocolService,
    private val objectMapper: ObjectMapper,
    services: CoreServiceContainer,
) : StudyService, ResourceExporter<StudySnapshot> {
    companion object {
        private const val APPLICATION_NAME_NOT_SET = "not-set"
    }

    final override val core = services.studyService

    override suspend fun getStudiesOverview(accountId: UUID): List<StudyOverview> =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val account =
                accountService.findByUUID(accountId)
                    ?: throw IllegalArgumentException("Account with id $accountId is not found.")

            account.carpClaims
                ?.filter { it is Claim.ManageStudy || it is Claim.LimitedManageStudy }
                ?.mapNotNull { claim: Claim ->
                    when (claim) {
                        is Claim.ManageStudy -> claim.studyId
                        is Claim.LimitedManageStudy -> claim.studyId
                        else -> null
                    }
                }
                ?.let { studyRepository.findAllByStudyIds(it) }
                ?.map {
                    val status = it.getStatus()
//                    val details = core.getStudyDetails(status.studyId)
//                    val owner = accountService.findByUUID(details.ownerId)
                    // TODO: Do we still need this?
                    StudyOverview(
                        studyId = status.studyId,
                        name = status.name,
                        createdOn = status.createdOn,
                        studyProtocolId = status.studyProtocolId,
                        canSetInvitation = status.canSetInvitation,
                        canSetStudyProtocol = status.canSetStudyProtocol,
                        canDeployToParticipants = status.canDeployToParticipants,
                        description = it.description,
                        createdBy = "",
                    )
                }
                ?: emptyList()
        }

    final override val dataFileName = "study.json"

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ): Collection<StudySnapshot> = setOf(studyRepository.getStudySnapshotById(studyId))

    override suspend fun invoke(request: StudyServiceRequest<*>): Any? =
        when (request) {
            is StudyServiceRequest.SetProtocol -> {
                val versionTag = protocolService.resolveVersionTag(request.protocol)
                val updatedProtocol =
                    request.protocol.copy(
                        applicationData =
                            mergeApplicationData(
                                request.protocol.applicationData,
                                mapOf("protocolVersionTag" to versionTag),
                            ),
                    )
                core.invoke(request.copy(protocol = updatedProtocol))
            }
            is StudyServiceRequest.GoLive -> {
                val details = core.getStudyDetails(request.studyId)
                val applicationName = extractApplicationName(details.protocolSnapshot?.applicationData)
                val applicationJson =
                    buildJsonObject {
                        put("studyId", JsonPrimitive(request.studyId.stringRepresentation))
                        put("applicationName", JsonPrimitive(applicationName ?: APPLICATION_NAME_NOT_SET))
                    }.toString()

                core.setInvitation(
                    request.studyId,
                    StudyInvitation(details.invitation.name, details.invitation.description, applicationJson),
                )
                core.invoke(request)
            }
            else -> core.invoke(request)
        }

    private fun extractApplicationName(applicationData: String?): String? {
        if (applicationData.isNullOrBlank()) return null
        val applicationDataNode = runCatching { objectMapper.readTree(applicationData) }.getOrNull() ?: return null
        return applicationDataNode.path("applicationName").asText().takeIf { it.isNotBlank() }
    }

    private fun mergeApplicationData(
        existingData: String?,
        fieldsToMerge: Map<String, String>,
    ): String {
        fun createMergedNode() =
            objectMapper
                .createObjectNode()
                .apply {
                    fieldsToMerge.forEach { (key, value) -> put(key, value) }
                }

        if (existingData.isNullOrBlank()) {
            return createMergedNode().toString()
        }

        val existingNode = runCatching { objectMapper.readTree(existingData) }.getOrNull()
        if (existingNode is ObjectNode) {
            fieldsToMerge.forEach { (key, value) -> existingNode.put(key, value) }
            return existingNode.toString()
        }

        return createMergedNode()
            .put("legacyApplicationData", existingData)
            .toString()
    }
}
