package dk.cachet.carp.webservices.export.command

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.exception.file.FileStorageException
import dk.cachet.carp.webservices.export.command.impl.ExportSummary
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.file.util.FileUtil
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExportSummaryTest {
    private val fileUtil = mockk<FileUtil>()
    private val resourceExporter = mockk<ResourceExporterService>()

    @Nested
    inner class Execute {
        @Test
        fun `should throw if zipping fails`() =
            runTest {
                val entry =
                    Export(
                        fileName = "fileName",
                        studyId = UUID.randomUUID().stringRepresentation,
                    )

                every { fileUtil.resolveFileStoragePathForFilenameAndRelativePath(any(), any()) } returns mockk()
                every { fileUtil.zipDirectory(any(), any()) } throws FileStorageException("Failed to zip.")
                every { fileUtil.deleteFile(any()) } answers { nothing }
                coEvery { resourceExporter.exportStudyData(any(), any(), any(), any(), any()) } answers { nothing }

                val command = ExportSummary(entry, null, resourceExporter, fileUtil, null)

                assertFailsWith<FileStorageException> { command.execute() }
            }

        @Test
        fun `should export and zip data`() =
            runTest {
                val entry =
                    Export(
                        fileName = "fileName",
                        studyId = UUID.randomUUID().stringRepresentation,
                    )

                every { fileUtil.resolveFileStoragePathForFilenameAndRelativePath(any(), any()) } returns mockk()
                every { fileUtil.zipDirectory(any(), any()) } answers { nothing }
                every { fileUtil.deleteFile(any()) } answers { nothing }
                coEvery { resourceExporter.exportStudyData(any(), any(), any(), any(), any()) } answers { nothing }

                val command = ExportSummary(entry, null, resourceExporter, fileUtil, null)
                command.execute()

                verify { fileUtil.zipDirectory(any(), any()) }
                verify(exactly = 0) { fileUtil.deleteFile(any()) }
            }
    }
}
