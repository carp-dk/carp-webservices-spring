package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.study.dto.StudyDataUploadsDto
import dk.cachet.carp.webservices.study.repository.StudyDataUploadStore
import dk.cachet.carp.webservices.study.service.StudyDataUploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class StudyDataUploadServiceImpl(
    private val studyDataUploadStore: StudyDataUploadStore,
) : StudyDataUploadService {
    override suspend fun getDataUploads(studyId: UUID): StudyDataUploadsDto {
        val id = studyId.stringRepresentation
        val row =
            withContext(Dispatchers.IO) {
                if (!studyDataUploadStore.studyExists(id)) throw ResourceNotFoundException("Study $id does not exist.")
                studyDataUploadStore.findDataUploads(id)
            }
        return StudyDataUploadsDto.from(id, row)
    }
}
