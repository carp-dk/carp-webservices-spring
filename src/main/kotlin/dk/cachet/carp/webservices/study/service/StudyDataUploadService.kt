package dk.cachet.carp.webservices.study.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.study.dto.StudyDataUploadsDto

interface StudyDataUploadService {
    /** Totals of the data uploaded to [studyId]; throws `ResourceNotFoundException` if the study does not exist. */
    suspend fun getDataUploads(studyId: UUID): StudyDataUploadsDto
}
