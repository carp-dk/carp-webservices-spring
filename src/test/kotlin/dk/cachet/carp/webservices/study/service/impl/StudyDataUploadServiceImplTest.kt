package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.study.dto.StudyDataUploadsDto
import dk.cachet.carp.webservices.study.repository.StudyDataUploadRow
import dk.cachet.carp.webservices.study.repository.StudyDataUploadStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudyDataUploadServiceImplTest {
    private val store = mockk<StudyDataUploadStore>()
    private val sut = StudyDataUploadServiceImpl(store)

    @Test
    fun `returns the totals of an existing study`() =
        runTest {
            val studyId = UUID.randomUUID()
            val row = StudyDataUploadRow(3, 30, Instant.parse("2025-09-01T12:00:00Z"))
            every { store.studyExists(studyId.stringRepresentation) } returns true
            every { store.findDataUploads(studyId.stringRepresentation) } returns row

            assertEquals(StudyDataUploadsDto.from(studyId.stringRepresentation, row), sut.getDataUploads(studyId))
        }

    @Test
    fun `an unknown study is not found and not queried`() =
        runTest {
            val studyId = UUID.randomUUID()
            every { store.studyExists(studyId.stringRepresentation) } returns false

            assertFailsWith<ResourceNotFoundException> { sut.getDataUploads(studyId) }
            verify(exactly = 0) { store.findDataUploads(any()) }
        }
}
