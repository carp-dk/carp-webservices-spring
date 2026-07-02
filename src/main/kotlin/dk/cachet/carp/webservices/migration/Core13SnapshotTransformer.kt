package dk.cachet.carp.webservices.migration

import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.decodeFromString
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Instant

@Component
class Core13SnapshotTransformer(
    private val objectMapper: ObjectMapper,
) {
    fun migrateDeployment(
        snapshotJson: String,
        updatedAt: Instant?,
    ): String {
        val snapshot =
            objectMapper.readTree(snapshotJson) as? ObjectNode
                ?: throw IllegalArgumentException("Deployment snapshot is not a JSON object.")
        val legacyStopped = snapshot.get("isStopped") ?: return validateDeployment(snapshotJson)

        if (legacyStopped.asBoolean()) {
            val stoppedOn = requireNotNull(updatedAt) { "Stopped deployment has no updated_at timestamp." }
            snapshot.put("stoppedOn", stoppedOn.toString())
        } else {
            snapshot.putNull("stoppedOn")
        }
        snapshot.remove("isStopped")

        return validateDeployment(snapshot.toString())
    }

    fun migrateRecruitment(snapshotJson: String): String {
        val snapshot =
            objectMapper.readTree(snapshotJson) as? ObjectNode
                ?: throw IllegalArgumentException("Recruitment snapshot is not a JSON object.")
        val groups = snapshot.get("participantGroups") as? ObjectNode ?: return validateRecruitment(snapshotJson)

        groups.properties().forEach { (_, groupNode) ->
            val group =
                groupNode as? ObjectNode
                    ?: throw IllegalArgumentException("Participant group is not a JSON object.")
            val participantIds = group.get("_participantIds") ?: return@forEach
            require(participantIds.isArray) { "_participantIds is not an array." }

            val assignments = objectMapper.createArrayNode()
            participantIds.forEach { participantId ->
                val assignedTo =
                    objectMapper.createObjectNode()
                        .put("__type", ASSIGNED_TO_ALL_TYPE)
                assignments.add(
                    objectMapper.createObjectNode()
                        .put("participantId", participantId.asString())
                        .set("assignedRoles", assignedTo),
                )
            }

            group.set("_roleAssignments", assignments)
            group.set("representation", objectMapper.createObjectNode().putNull("name"))
            group.remove("_participantIds")
        }

        return validateRecruitment(snapshot.toString())
    }

    fun validateDeployment(snapshotJson: String): String =
        snapshotJson.also { WS_JSON.decodeFromString<StudyDeploymentSnapshot>(it) }

    fun validateRecruitment(snapshotJson: String): String =
        snapshotJson.also { WS_JSON.decodeFromString<RecruitmentSnapshot>(it) }

    companion object {
        const val ASSIGNED_TO_ALL_TYPE = "dk.cachet.carp.common.application.users.AssignedTo.All"
    }
}
