package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.Study
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface StudyRepository : JpaRepository<Study, Int> {
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM studies WHERE snapshot->>'id' = ?1",
    )
    fun getByStudyId(id: String): Study?

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM studies WHERE snapshot->>'ownerId' = ?1",
    )
    fun findAllByOwnerId(ownerId: String): List<Study>

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM studies WHERE snapshot->>'id' IN ?1",
    )
    fun findAllByStudyIds(studyIds: List<String>): List<Study>

    @Query(
        nativeQuery = true,
        value = "SELECT count(*) FROM studies WHERE snapshot->>'isLive' = 'true'",
    )
    fun countLiveStudies(): Long

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM studies WHERE snapshot->>'id' = ?1",
    )
    fun deleteByStudyId(studyId: String)
}
