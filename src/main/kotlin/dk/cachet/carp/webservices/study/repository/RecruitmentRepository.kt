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

    @Query(
        value = """
                SELECT count(*)
                FROM public.recruitments, 
                     jsonb_array_elements(snapshot->'participants') arr(elem)
                WHERE snapshot->>'studyId' = :studyId
                AND (
                    :search IS NULL 
                    OR elem->'accountIdentity'->>'username' ILIKE '%' || :search || '%'
                    OR elem->'accountIdentity'->>'emailAddress' ILIKE '%' || :search || '%'
                )
        """,
        nativeQuery = true,
    )
    fun countRecruitmentParticipantsByStudyIdAndSearch(
        studyId: String,
        search: String?,
    ): Int

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
                UPDATE recruitments
                SET snapshot = jsonb_set(
                    jsonb_set(
                        snapshot,
                        '{participantGroups}',
                        COALESCE(snapshot->'participantGroups', '{}'::jsonb)
                        || CAST(:participantGroups AS jsonb)
                    ),
                    '{participants}',
                    COALESCE(snapshot->'participants', '[]'::jsonb)
                    || CAST(:participants AS jsonb)
                )
                WHERE snapshot->>'studyId' = :studyId;
        """,
    )
    fun bulkAddParticipantsAndGroups(
        studyId: String,
        participants: String,
        participantGroups: String,
    )
}
