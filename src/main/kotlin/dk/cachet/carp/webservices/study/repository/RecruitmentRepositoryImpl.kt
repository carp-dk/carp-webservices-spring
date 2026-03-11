package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

class RecruitmentRepositoryImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : RecruitmentRepositoryCustom {
    companion object {
        private const val DEPLOYED_PARTICIPANTS_CTE =
            """
            WITH deployed_participants AS (
                SELECT DISTINCT participant_id
                FROM public.recruitments,
                     jsonb_each(snapshot->'participantGroups') groups(group_id, group_value),
                     jsonb_array_elements_text(group_value->'_participantIds') participant_ids(participant_id)
                WHERE snapshot->>'studyId' = :studyId
                AND group_value->>'isDeployed' = 'true'
            )
            """
    }

    override fun findRecruitmentParticipantsByStudyIdAndSearchAndLimitAndOffset(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDescending: Boolean?,
        sortBy: ParticipantOrderBy?,
    ): String? =
        executeLegacyParticipantQuery(
            studyId,
            offset,
            limit,
            search,
            when (isDescending) {
                true -> SortDirection.Desc
                false -> SortDirection.Asc
                null -> null
            },
            sortBy,
        )

    override fun queryParticipantAccounts(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDeployed: Boolean?,
        sortDirection: SortDirection?,
        sortBy: ParticipantOrderBy?,
    ): List<ParticipantAccountQueryRow> =
        executeParticipantAccountQuery(studyId, offset, limit, search, isDeployed, sortDirection, sortBy)

    override fun countQueryParticipantAccounts(
        studyId: String,
        search: String?,
        isDeployed: Boolean?,
    ): Int {
        val searchCondition = buildParticipantSearchCondition("participant", search)
        val deploymentCondition = buildDeploymentCondition(isDeployed)

        val sql = """
            $DEPLOYED_PARTICIPANTS_CTE
            SELECT COUNT(*)
            FROM public.recruitments,
                 jsonb_array_elements(snapshot->'participants') arr(participant)
            LEFT JOIN deployed_participants deployed_participant
                ON deployed_participant.participant_id = participant->>'id'
            WHERE snapshot->>'studyId' = :studyId
            $searchCondition
            $deploymentCondition
        """

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("studyId", studyId)
        if (search != null) query.setParameter("search", search)

        return (query.singleResult as Number).toInt()
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun executeLegacyParticipantQuery(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        sortDirection: SortDirection?,
        sortBy: ParticipantOrderBy?,
    ): String? {
        val direction = if (sortDirection == SortDirection.Desc) "DESC" else "ASC"

        val searchCondition =
            if (search != null) {
                """
            AND (
                elem->>'id' ILIKE CONCAT('%', :search, '%')
                OR
                elem->'accountIdentity'->>'username' ILIKE CONCAT('%', :search, '%')
                OR elem->'accountIdentity'->>'emailAddress' ILIKE CONCAT('%', :search, '%')
            )
        """
            } else {
                ""
            }

        val orderByClause =
            when (sortBy) {
                ParticipantOrderBy.AccountIdentity ->
                    """
                    COALESCE(
                         elem->'accountIdentity'->>'username',
                         elem->'accountIdentity'->>'emailAddress'
                    ) $direction
                """
                else ->
                    if (sortDirection == null) {
                        "idx"
                    } else {
                        """
                        COALESCE(
                             elem->'accountIdentity'->>'username',
                             elem->'accountIdentity'->>'emailAddress'
                        ) $direction
                    """
                    }
            }

        val sql = """
            SELECT jsonb_agg(elem) AS participants_
            FROM (
                SELECT elem
                FROM public.recruitments,
                     jsonb_array_elements(snapshot->'participants') WITH ORDINALITY arr(elem, idx)
                WHERE snapshot->>'studyId' = :studyId
                $searchCondition
                ORDER BY $orderByClause
                LIMIT :limit OFFSET :offset
            ) subquery
        """

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("studyId", studyId)
        query.setParameter("limit", limit)
        query.setParameter("offset", offset)
        if (search != null) query.setParameter("search", search)

        return query.singleResult as? String
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun executeParticipantAccountQuery(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDeployed: Boolean?,
        sortDirection: SortDirection?,
        sortBy: ParticipantOrderBy?,
    ): List<ParticipantAccountQueryRow> {
        val direction = if (sortDirection == SortDirection.Desc) "DESC" else "ASC"
        val searchCondition = buildParticipantSearchCondition("elem", search)
        val deploymentCondition = buildDeploymentCondition(isDeployed)

        val orderByClause =
            when (sortBy) {
                ParticipantOrderBy.IsDeployed ->
                    """
                    CASE WHEN deployed_participant.participant_id IS NULL THEN 0 ELSE 1 END $direction,
                    COALESCE(
                        elem->'accountIdentity'->>'username',
                        elem->'accountIdentity'->>'emailAddress'
                    ) $direction,
                    idx $direction
                    """
                ParticipantOrderBy.AccountIdentity ->
                    """
                    COALESCE(
                        elem->'accountIdentity'->>'username',
                        elem->'accountIdentity'->>'emailAddress'
                    ) $direction
                """
                else ->
                    if (sortDirection == null) {
                        "idx"
                    } else {
                        """
                        COALESCE(
                            elem->'accountIdentity'->>'username',
                            elem->'accountIdentity'->>'emailAddress'
                        ) $direction
                    """
                    }
            }

        // The new POST query supports unpaged requests, so only emit LIMIT/OFFSET when both values are provided.
        val paginationClause =
            if (limit != null && offset != null) {
                "LIMIT :limit OFFSET :offset"
            } else {
                ""
            }

        val sql = """
            $DEPLOYED_PARTICIPANTS_CTE
            SELECT
                elem AS participant,
                deployed_participant.participant_id IS NOT NULL AS is_deployed
            FROM public.recruitments,
                 jsonb_array_elements(snapshot->'participants') WITH ORDINALITY arr(elem, idx)
            LEFT JOIN deployed_participants deployed_participant
                ON deployed_participant.participant_id = elem->>'id'
            WHERE snapshot->>'studyId' = :studyId
            $searchCondition
            $deploymentCondition
            ORDER BY $orderByClause
            $paginationClause
        """

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("studyId", studyId)
        // Keep the parameter bindings aligned with the generated SQL to avoid invalid unpaged queries.
        if (limit != null && offset != null) {
            query.setParameter("limit", limit)
            query.setParameter("offset", offset)
        }
        if (search != null) query.setParameter("search", search)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.map { row ->
            ParticipantAccountQueryRow(
                participantJson = row[0].toString(),
                isDeployed = row[1] as Boolean,
            )
        }
    }

    private fun buildParticipantSearchCondition(
        participantColumn: String,
        search: String?,
    ): String =
        if (search != null) {
            """
            AND (
                $participantColumn->>'id' ILIKE CONCAT('%', :search, '%')
                OR
                $participantColumn->'accountIdentity'->>'username' ILIKE CONCAT('%', :search, '%')
                OR $participantColumn->'accountIdentity'->>'emailAddress' ILIKE CONCAT('%', :search, '%')
            )
        """
        } else {
            ""
        }

    private fun buildDeploymentCondition(isDeployed: Boolean?): String =
        when (isDeployed) {
            true -> "AND deployed_participant.participant_id IS NOT NULL"
            false -> "AND deployed_participant.participant_id IS NULL"
            null -> ""
        }
}
