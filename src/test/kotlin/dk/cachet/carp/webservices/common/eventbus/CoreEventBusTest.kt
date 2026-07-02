package dk.cachet.carp.webservices.common.eventbus

import dk.cachet.carp.common.application.ApplicationData
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.DeploymentService
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.studies.application.StudyDetails
import dk.cachet.carp.studies.application.StudyService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.env.Environment
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class CoreEventBusTest {
    private val rabbitTemplate: RabbitTemplate = mockk()
    private val environment: Environment = mockk()

    @Nested
    inner class Publish {
        @BeforeEach
        fun setup() {
            every { rabbitTemplate.convertAndSend(any(), any<String>()) } returns Unit
            every { environment.getProperty("rabbit.study.queue") } returns "studyQueue"
            every { environment.getProperty("rabbit.deployment.queue") } returns "deploymentQueue"
        }

        @Test
        fun `should publish when study service`() {
            runTest {
                val event =
                    StudyService.Event.StudyCreated(
                        study =
                            StudyDetails(
                                studyId = UUID.randomUUID(),
                                ownerId = UUID.randomUUID(),
                                name = "dsa",
                                createdOn = Instant.parse("2020-04-02T00:00:00.000Z"),
                                description = "dsa",
                                invitation =
                                    StudyInvitation(
                                        name = "dsa",
                                        description = "dsa",
                                        applicationData = ApplicationData(""),
                                    ),
                                protocolSnapshot = null,
                            ),
                    )
                val publishingService = StudyService::class
                val sut = CoreEventBus(rabbitTemplate, environment)

                sut.publish(publishingService, event)

                verify {
                    rabbitTemplate.convertAndSend("studyQueue", any<String>())
                }
            }
        }

        @Test
        fun `should publish when deployment`() {
            runTest {
                val event =
                    DeploymentService.Event.StudyDeploymentCreated(
                        studyDeploymentId = UUID.randomUUID(),
                        protocol =
                            StudyProtocolSnapshot(
                                id = UUID.randomUUID(),
                                createdOn = Clock.System.now(),
                                version = 1,
                                ownerId = UUID.randomUUID(),
                                name = "name",
                            ),
                        invitations = emptyList(),
                        connectedDevicePreregistrations = mapOf(),
                    )
                val deploymentService = DeploymentService::class
                val sut = CoreEventBus(rabbitTemplate, environment)

                sut.publish(deploymentService, event)

                verify {
                    rabbitTemplate.convertAndSend("deploymentQueue", any<String>())
                }
            }
        }
    }
}
