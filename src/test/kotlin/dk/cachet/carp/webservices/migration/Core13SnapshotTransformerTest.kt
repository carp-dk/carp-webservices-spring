package dk.cachet.carp.webservices.migration

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class Core13SnapshotTransformerTest {
    private val objectMapper = ObjectMapper()
    private val transformer = Core13SnapshotTransformer(objectMapper)

    @Test
    fun `migrates active deployment and is idempotent`() {
        val legacy = deploymentSnapshot(isStopped = false)

        val migrated = transformer.migrateDeployment(legacy, Instant.parse("2025-01-01T00:00:00Z"))
        val node = objectMapper.readTree(migrated)

        assertFalse(node.has("isStopped"))
        assertNull(node.get("stoppedOn").asString(null))
        WS_JSON.decodeFromString<StudyDeploymentSnapshot>(migrated)
        assertEquals(migrated, transformer.migrateDeployment(migrated, null))
    }

    @Test
    fun `uses updated timestamp for stopped deployment`() {
        val updatedAt = Instant.parse("2025-01-01T00:00:00.123456Z")

        val migrated = transformer.migrateDeployment(deploymentSnapshot(isStopped = true), updatedAt)

        assertEquals(updatedAt.toString(), objectMapper.readTree(migrated).get("stoppedOn").asString())
    }

    @Test
    fun `migrates recruitment participants to assigned all and is idempotent`() {
        val participantId = UUID.randomUUID().stringRepresentation
        val legacy = recruitmentSnapshot(participantId)

        val migrated = transformer.migrateRecruitment(legacy)
        val group = objectMapper.readTree(migrated).get("participantGroups").properties().first().value
        val assignment = group.get("_roleAssignments").first()

        assertFalse(group.has("_participantIds"))
        assertEquals(participantId, assignment.get("participantId").asString())
        assertEquals(
            Core13SnapshotTransformer.ASSIGNED_TO_ALL_TYPE,
            assignment.get("assignedRoles").get("__type").asString(),
        )
        assertNull(group.get("representation").get("name").asString(null))
        WS_JSON.decodeFromString<RecruitmentSnapshot>(migrated)
        assertEquals(migrated, transformer.migrateRecruitment(migrated))
    }

    private fun deploymentSnapshot(isStopped: Boolean): String =
        """
        {
          "id":"${UUID.randomUUID()}",
          "createdOn":"2024-01-01T00:00:00Z",
          "version":0,
          "studyProtocolSnapshot":{
            "id":"${UUID.randomUUID()}",
            "createdOn":"2024-01-01T00:00:00Z",
            "version":0,
            "ownerId":"${UUID.randomUUID()}",
            "name":"Protocol"
          },
          "participants":[],
          "deviceRegistrationHistory":{},
          "deployedDevices":[],
          "invalidatedDeployedDevices":[],
          "startedOn":null,
          "isStopped":$isStopped
        }
        """.trimIndent()

    private fun recruitmentSnapshot(participantId: String): String {
        val groupId = UUID.randomUUID().stringRepresentation
        return """
            {
              "id":"${UUID.randomUUID()}",
              "createdOn":"2024-01-01T00:00:00Z",
              "version":0,
              "studyId":"${UUID.randomUUID()}",
              "studyProtocol":null,
              "invitation":null,
              "participants":[],
              "participantGroups":{
                "$groupId":{
                  "id":"$groupId",
                  "_participantIds":["$participantId"],
                  "isDeployed":false
                }
              }
            }
            """.trimIndent()
    }
}
