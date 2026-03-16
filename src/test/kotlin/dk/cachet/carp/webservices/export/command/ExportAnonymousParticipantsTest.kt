package dk.cachet.carp.webservices.export.command

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.StudyStatus
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.export.command.impl.ExportAnonymousParticipants
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.file.util.FileUtil
import dk.cachet.carp.webservices.study.domain.AnonymousParticipantRequest
import dk.cachet.carp.webservices.study.service.AnonymousService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertFalse

class ExportAnonymousParticipantsTest {
    private val services = mockk<CoreServiceContainer>()
    private val accountService = mockk<AccountService>()
    private val anonymousService = mockk<AnonymousService>()
    private val fileUtil = mockk<FileUtil>()

    private val payload =
        AnonymousParticipantRequest(
            amountOfAccounts = 1,
            expirationSeconds = 1L,
            clientId = "id",
            redirectUri = "uri",
            participantRoleName = "role",
            subdomain = null,
        )

    @Nested
    inner class CanExecute {
        private val entry =
            mockk<Export> {
                every { studyId } returns UUID.randomUUID().stringRepresentation
            }

        @Test
        fun `should return false if there is no protocol`() {
            every { services.studyService } returns
                    mockk {
                        coEvery { getStudyDetails(any()) } returns
                                mockk {
                                    every { protocolSnapshot } returns null
                                }
                        coEvery { getStudyStatus(any()) } returns
                                mockk {
                                    StudyStatus.Live
                                }
                    }

            val command =
                ExportAnonymousParticipants(
                    entry,
                    payload,
                    services,
                    anonymousService,
                    accountService,
                    fileUtil,
                )
            val canExecute = command.canExecute().first

            assertFalse(canExecute)
        }

        @Test
        fun `should return false if the participant role is not in the protocol`() {
            every { services.studyService } returns
                    mockk {
                        coEvery { getStudyDetails(any()) } returns
                                mockk {
                                    every { protocolSnapshot } returns
                                            mockk {
                                                every { participantRoles } returns emptySet()
                                            }
                                }
                        coEvery { getStudyStatus(any()) } returns
                                mockk {
                                    StudyStatus.Live
                                }
                    }

            val command =
                ExportAnonymousParticipants(
                    entry,
                    payload,
                    services,
                    anonymousService,
                    accountService,
                    fileUtil,
                )
            val canExecute = command.canExecute().first

            assertFalse(canExecute)
        }

        @Test
        fun `should return false if the amount of accounts is not in the range`() {
            every { services.studyService } returns
                    mockk {
                        coEvery { getStudyDetails(any()) } returns
                                mockk {
                                    every { protocolSnapshot } returns
                                            mockk {
                                                every { participantRoles } returns
                                                        setOf(
                                                            mockk {
                                                                every { role } returns payload.participantRoleName
                                                            },
                                                        )
                                            }
                                }
                        coEvery { getStudyStatus(any()) } returns
                                mockk {
                                    StudyStatus.Live
                                }
                    }

            val command =
                ExportAnonymousParticipants(
                    entry,
                    payload.copy(amountOfAccounts = 0),
                    services,
                    anonymousService,
                    accountService,
                    fileUtil,
                )
            val canExecute = command.canExecute().first

            assertFalse(canExecute)
        }

        @Test
        fun `should return false if the study is not live`() {
            every { services.studyService } returns
                    mockk {
                        coEvery { getStudyDetails(any()) } returns
                                mockk {
                                    every { protocolSnapshot } returns
                                            mockk {
                                                every { participantRoles } returns
                                                        setOf(
                                                            mockk {
                                                                every { role } returns payload.participantRoleName
                                                            },
                                                        )
                                            }
                                }
                        coEvery { getStudyStatus(any()) } returns
                                mockk {
                                    StudyStatus.Configuring
                                }
                    }

            val command =
                ExportAnonymousParticipants(
                    entry,
                    payload,
                    services,
                    anonymousService,
                    accountService,
                    fileUtil,
                )
            val canExecute = command.canExecute().first

            assertFalse(canExecute)
        }
    }
}
