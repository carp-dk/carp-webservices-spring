package dk.cachet.carp.webservices.deployment.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.deployment.domain.StudyDeployment
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.data.domain.AuditorAware
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreDeploymentRepositoryTest {
    private val studyDeploymentRepository: StudyDeploymentRepository = mockk()
    private val auth: AuthorizationService = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private val validationMessages: MessageBase = mockk()
    private val jbdcTemplate: JdbcTemplate = mockk()
    private val auditorAware: AuditorAware<String> = mockk()

    @Nested
    inner class Remove {
        @Test
        fun `removes deployment`() =
            runTest {
                val mockUUID1 = UUID.randomUUID()
                val mockId = 123

                val studyDeploymentIds = setOf(mockUUID1)

                val studyDeployment1 =
                    mockk<StudyDeployment> {
                        every { id } returns mockId
                        every { snapshot } returns getMockStudyDeployment(mockUUID1)
                    }

                val studyDeployments = listOf(studyDeployment1)

                coEvery { studyDeploymentRepository.findAllByStudyDeploymentIds(any()) } returns studyDeployments
                coEvery { studyDeploymentRepository.deleteByDeploymentIds(any()) } returns Unit
                coEvery { auth.revokeClaimsFromAllAccounts(any()) } returns Unit
                coEvery { jbdcTemplate.batchUpdate(any()) } returns IntArray(1)
                coEvery { auditorAware.currentAuditor.orElse(any()) } returns "system"

                val sut =
                    CoreDeploymentRepository(
                        studyDeploymentRepository,
                        objectMapper,
                        validationMessages,
                        auth,
                        jbdcTemplate,
                        auditorAware,
                    )

                val result = sut.remove(studyDeploymentIds)

                coEvery { studyDeploymentRepository.findAllByStudyDeploymentIds(any()) }
                coEvery { studyDeploymentRepository.deleteByDeploymentIds(any()) }

                val claims =
                    studyDeploymentIds.map {
                        Claim.InDeployment(it)
                    }.toSet()
                coEvery { auth.revokeClaimsFromAllAccounts(claims) }
                assertEquals(result.size, 1)
                assertEquals(result.first(), mockUUID1)
            }

        private fun getMockStudyDeployment(uuid: UUID): JsonNode {
            val om = ObjectMapper()
            val studyDeployment: ObjectNode = om.createObjectNode()
            studyDeployment.put("id", uuid.stringRepresentation)
            studyDeployment.put("createdOn", "2024-10-23T08:41:07.850883Z")
            studyDeployment.put("version", 0)
            studyDeployment.put("startedOn", "2024-10-23T08:41:07.850883Z")
            studyDeployment.put("isStopped", false)
            studyDeployment.putArray("participants")

            val studyProtocolSnapshot = om.createObjectNode()
            studyProtocolSnapshot.put("id", UUID.randomUUID().stringRepresentation)
            studyProtocolSnapshot.put("createdOn", "2024-10-23T08:41:07.850883Z")
            studyProtocolSnapshot.put("version", 0)
            studyProtocolSnapshot.put("name", "name")
            studyProtocolSnapshot.put("ownerId", UUID.randomUUID().stringRepresentation)

            val primaryDevice = om.createObjectNode()
            primaryDevice.put("__type", "dk.cachet.carp.common.application.devices.WebBrowser")
            primaryDevice.put("roleName", "ICAT Web Browser")
            primaryDevice.put("isPrimaryDevice", true)
            primaryDevice.set<ObjectNode>("defaultSamplingConfiguration", om.createObjectNode())

            studyProtocolSnapshot.putArray("primaryDevices").add(primaryDevice)

            studyDeployment.set<ObjectNode>("studyProtocolSnapshot", studyProtocolSnapshot)

            return studyDeployment
        }
    }
}
