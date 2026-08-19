package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.Recruitment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface RecruitmentRepository : JpaRepository<Recruitment, Int>, RecruitmentRepositoryCustom {
    @Query(value = "SELECT * FROM recruitments WHERE snapshot->>'studyId' = ?1", nativeQuery = true)
    fun findRecruitmentByStudyId(studyId: String): Recruitment?

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM recruitments WHERE snapshot->>'studyId' = ?1",
    )
    fun deleteByStudyId(studyId: String)

    /**
     * Resolves the recruitment owning a participant group. The blob's `participantGroups` is empty —
     * groups live in the normalized tables.
     */
    @Query(
        value =
            """
            SELECT r.* FROM recruitments r
            JOIN recruitment_participant_groups g ON g.recruitment_id = r.id
            WHERE g.group_id = ?1
            """,
        nativeQuery = true,
    )
    fun findRecruitmentByNormalizedGroupId(groupId: String): Recruitment?

    /**
     * Resolves the study a deployment belongs to. Participant group ids are study deployment ids, so a
     * deployment maps to exactly one recruitment. Served by the unique index on `group_id`, and does not
     * load the snapshot — cheap enough for the authorization hot path.
     */
    @Query(
        value = "SELECT study_id FROM recruitment_participant_groups WHERE group_id = ?1",
        nativeQuery = true,
    )
    fun findStudyIdByNormalizedGroupId(groupId: String): String?
}
