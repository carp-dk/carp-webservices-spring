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

    @Query(
        value =
            """
            SELECT *
            FROM recruitments
            WHERE (snapshot->'participantGroups') @> jsonb_build_object(CAST(:groupId AS text), '{}'::jsonb)
            """,
        nativeQuery = true,
    )
    fun findRecruitmentByParticipantGroupId(groupId: String): Recruitment?

    /**
     * Resolves the study a deployment belongs to without deserializing the (potentially huge)
     * recruitment snapshot. Participant group ids are study deployment ids, so a deployment maps to
     * exactly one recruitment. The `@>` containment predicate is served by the GIN index on
     * `snapshot->'participantGroups'`.
     */
    @Query(
        value =
            """
            SELECT snapshot->>'studyId'
            FROM recruitments
            WHERE (snapshot->'participantGroups') @> jsonb_build_object(CAST(:deploymentId AS text), '{}'::jsonb)
            """,
        nativeQuery = true,
    )
    fun findStudyIdByDeploymentId(deploymentId: String): String?

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM recruitments WHERE snapshot->>'studyId' = ?1",
    )
    fun deleteByStudyId(studyId: String)

    /**
     * Relational equivalent of [findRecruitmentByParticipantGroupId] over the normalized tables.
     * Used once reads are served from the tables, where the blob's `participantGroups` is empty.
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
     * Relational equivalent of [findStudyIdByDeploymentId] over the normalized tables (indexed unique
     * `group_id`). Cheap enough for the authorization hot path and does not load the snapshot.
     */
    @Query(
        value = "SELECT study_id FROM recruitment_participant_groups WHERE group_id = ?1",
        nativeQuery = true,
    )
    fun findStudyIdByNormalizedGroupId(groupId: String): String?

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
