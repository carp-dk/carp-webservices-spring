package dk.cachet.carp.webservices.export.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.export.command.ExportCommand
import dk.cachet.carp.webservices.export.command.ExportCommandInvoker
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.domain.ExportStatus
import dk.cachet.carp.webservices.export.domain.ExportType
import dk.cachet.carp.webservices.export.repository.ExportRepository
import dk.cachet.carp.webservices.export.service.impl.ExportServiceImpl
import dk.cachet.carp.webservices.file.service.FileStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExportServiceTest {
    private val repository = mockk<ExportRepository>()
    private val invoker = mockk<ExportCommandInvoker>()
    private val fileStorage = mockk<FileStorage>()

    @Nested
    inner class CreateExport {
        private val command = mockk<ExportCommand>()

        @Test
        fun `should return existing if found`() {
            val id = UUID.randomUUID().stringRepresentation
            val studyId = UUID.randomUUID().stringRepresentation

            val existingEntry =
                Export(
                    id = id,
                    status = ExportStatus.AVAILABLE,
                    studyId = studyId,
                )

            val newEntry =
                Export(
                    id = id,
                    status = ExportStatus.IN_PROGRESS,
                    studyId = studyId,
                )

            every { command.entry } returns newEntry
            every { repository.findByIdAndStudyId(id, studyId) } returns existingEntry

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            sut.createExport(command)

            verify(exactly = 0) { repository.save(any()) }
            verify(exactly = 0) { invoker.invoke(any()) }
        }

        @Test
        fun `should invoke command and save entry`() {
            val entry =
                Export(
                    id = UUID.randomUUID().stringRepresentation,
                    status = ExportStatus.IN_PROGRESS,
                    studyId = UUID.randomUUID().stringRepresentation,
                )

            every { command.entry } returns entry
            every { repository.findByIdAndStudyId(entry.id, entry.studyId) } returns null
            every { invoker.invoke(command) } answers { nothing }
            every { repository.save(entry) } returns entry

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            sut.createExport(command)

            verify { invoker.invoke(command) }
            verify { repository.save(entry) }
        }
    }

    @Nested
    inner class DeleteExport {
        @Test
        fun `should throw if export is in progress`() {
            val id = UUID.randomUUID()
            val studyId = UUID.randomUUID()

            val entry =
                Export(
                    id = id.stringRepresentation,
                    status = ExportStatus.IN_PROGRESS,
                    studyId = studyId.stringRepresentation,
                )

            every { repository.findByIdAndStudyId(any(), any()) } returns entry

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            assertFailsWith<ConflictException> { sut.deleteExport(id, studyId) }

            verify(exactly = 0) { repository.delete(any()) }
            verify(exactly = 0) { fileStorage.deleteFile(any()) }
        }

        @Test
        fun `should throw if entry doesn't exist`() {
            val id = UUID.randomUUID()
            val studyId = UUID.randomUUID()

            every { repository.findByIdAndStudyId(any(), any()) } returns null

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            assertFailsWith<IllegalArgumentException> { sut.deleteExport(id, studyId) }

            verify(exactly = 0) { repository.delete(any()) }
            verify(exactly = 0) { fileStorage.deleteFile(any()) }
        }

        @Test
        fun `should delete export and file`() {
            val id = UUID.randomUUID()
            val studyId = UUID.randomUUID()

            val entry =
                Export(
                    id = id.stringRepresentation,
                    status = ExportStatus.AVAILABLE,
                    studyId = studyId.stringRepresentation,
                )

            every { repository.findByIdAndStudyId(any(), any()) } returns entry
            every { repository.delete(entry) } answers { nothing }
            every { fileStorage.deleteFileAtPath(entry.fileName, any()) } returns true

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            sut.deleteExport(id, studyId)

            verify { repository.delete(entry) }
            verify { fileStorage.deleteFileAtPath(entry.fileName, any<Path>()) }
        }
    }

    @Nested
    inner class CleanupExpiredExports {
        @Test
        fun `should delete all exports older than retention days`() {
            val retentionDays = 7
            val expiresBefore = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000
            val exportsToDelete =
                mutableListOf(
                    Export(
                        id = "1",
                        fileName = "file1",
                        relativePath = "path1",
                    ),
                    Export(
                        id = "2",
                        fileName = "file2",
                        relativePath = "path2",
                    ),
                )

            every { repository.getAllByUpdatedAtIsBefore(any()) } returns exportsToDelete
            every { repository.delete(any()) } answers { nothing }
            every { fileStorage.deleteFileAtPath(any(), any()) } returns true

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            sut.cleanupExpiredExports(retentionDays)

            verify(exactly = exportsToDelete.size) { repository.delete(any()) }
            verify(exactly = exportsToDelete.size) { fileStorage.deleteFileAtPath(any(), any()) }

            verify {
                repository.getAllByUpdatedAtIsBefore(
                    match {
                        val tolerance = 5000
                        Math.abs(it.toEpochMilli() - expiresBefore) <= tolerance
                    },
                )
            }
        }

        @Test
        fun `should retain anonymous participants exports`() {
            val retentionDays = 7
            val anonymousParticipantsExport =
                Export(
                    id = "1",
                    fileName = "file2",
                    relativePath = "path2",
                    type = ExportType.ANONYMOUS_PARTICIPANTS,
                )

            every { repository.getAllByUpdatedAtIsBefore(any()) } returns mutableListOf(anonymousParticipantsExport)

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            sut.cleanupExpiredExports(retentionDays)

            verify(exactly = 0) { repository.delete(anonymousParticipantsExport) }
            verify(exactly = 0) { fileStorage.deleteFileAtPath(any(), any()) }
        }

        @Test
        fun `should continue deletion if something throws`() {
            val retentionDays = 7
            val exportsToDelete =
                mutableListOf(
                    Export(
                        id = "1",
                        fileName = "file1",
                        relativePath = "path1",
                    ),
                    Export(
                        id = "2",
                        fileName = "file2",
                        relativePath = "path2",
                    ),
                )

            every { repository.getAllByUpdatedAtIsBefore(any()) } returns exportsToDelete
            every { repository.delete(any()) } answers { nothing }
            every { fileStorage.deleteFileAtPath("file1", Path.of("path1")) } returns true
            every { fileStorage.deleteFileAtPath("file2", Path.of("path2")) } throws IOException(";(")

            val sut = ExportServiceImpl(repository, invoker, fileStorage)
            sut.cleanupExpiredExports(retentionDays)

            verify(exactly = exportsToDelete.size) { repository.delete(any()) }
            verify(exactly = exportsToDelete.size) { fileStorage.deleteFileAtPath(any(), any()) }
            assertThrows<IOException> {
                fileStorage.deleteFileAtPath("file2", Path.of("path2"))
            }
        }
    }
}
