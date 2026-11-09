package dk.cachet.carp.webservices.study.dto

import dk.cachet.carp.webservices.study.repository.StudyDataUploadRow
import java.time.Instant

/** Data uploaded to one study, across the data streams of its deployed participant groups. */
data class StudyDataUploadsDto(
    val studyId: String,
    val dataStreamCount: Long,
    /** Measurements uploaded across all data streams; see `StudyDataUploadStore` for how this is counted. */
    val measurementCount: Long,
    val lastDataUploadAt: Instant?,
) {
    companion object {
        fun from(
            studyId: String,
            row: StudyDataUploadRow,
        ) = StudyDataUploadsDto(studyId, row.dataStreamCount, row.measurementCount, row.lastDataUploadAt)
    }
}
