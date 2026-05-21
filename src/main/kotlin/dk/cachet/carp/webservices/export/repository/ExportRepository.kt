package dk.cachet.carp.webservices.export.repository

import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.domain.ExportStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface ExportRepository : JpaRepository<Export, String> {
    fun findByIdAndStudyId(
        id: String,
        studyId: String,
    ): Export?

    fun findAllByStudyId(studyId: String): List<Export>

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM exports WHERE study_id = :studyId")
    fun deleteByStudyId(studyId: String)

    @Modifying
    @Transactional
    @Query("UPDATE exports SET status = :status WHERE id = :exportId")
    fun updateExportStatus(
        status: ExportStatus,
        exportId: String,
    )

    fun getAllByUpdatedAtIsBefore(updatedAtBefore: Instant): MutableList<Export>
}
