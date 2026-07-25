package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

/**
 * Participant search/list/count over the normalized recruitment tables (`recruitment_participants`,
 * `recruitment_participant_groups`, `recruitment_participant_group_members`) — the read-model half of
 * the participant-group normalization (see docs/participant-group-normalization.md).
 *
 * These previously fanned out over `recruitments.snapshot` JSONB. They now query the typed tables
 * (indexed `pg_trgm` search on `username`/`email_address`, relational join for the deployed flag) and
 * reconstruct the exact participant JSON via `jsonb_build_object`, so [ParticipantAccountQueryRow] and
 * the service-layer consumers are unchanged.
 */
class RecruitmentRepositoryImpl(
    @PersistenceContext private val entityManager: EntityManager,
) : RecruitmentRepositoryCustom {
    companion object {
        private const val EMAIL_IDENTITY_TYPE = "dk.cachet.carp.common.application.users.EmailAccountIdentity"
        private const val USERNAME_IDENTITY_TYPE = "dk.cachet.carp.common.application.users.UsernameAccountIdentity"

        /** Rebuilds the core `Participant` JSON (`{accountIdentity:{__type,..}, id}`) from columns of [alias]. */
        private fun participantJson(alias: String): String =
            """
            jsonb_build_object(
                'accountIdentity',
                CASE WHEN $alias.account_identity_type = 'email'
                    THEN jsonb_build_object('__type', '$EMAIL_IDENTITY_TYPE', 'emailAddress', $alias.email_address)
                    ELSE jsonb_build_object('__type', '$USERNAME_IDENTITY_TYPE', 'username', $alias.username)
                END,
                'id', $alias.participant_id
            )
            """

        /** Per-study map of participant -> its deployed group id (participant is deployed iff present). */
        private const val DEPLOYED_PARTICIPANTS_CTE =
            """
            WITH deployed_participants AS (
                SELECT DISTINCT ON (m.participant_id)
                    m.participant_id AS participant_id,
                    m.group_id AS deployment_id
                FROM recruitment_participant_group_members m
                JOIN recruitment_participant_groups g ON g.group_id = m.group_id
                WHERE g.study_id = :studyId AND g.is_deployed
                ORDER BY m.participant_id, m.group_id
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
    ): String? {
        val direction = if (isDescending == true) "DESC" else "ASC"
        val orderByClause =
            if (sortBy == ParticipantOrderBy.AccountIdentity || isDescending != null) {
                "COALESCE(p.username, p.email_address) $direction"
            } else {
                "p.sort_order"
            }

        val sql = """
            SELECT jsonb_agg(participant)::text AS participants_
            FROM (
                SELECT ${participantJson("p")} AS participant
                FROM recruitment_participants p
                WHERE p.study_id = :studyId
                ${buildSearchCondition("p", search)}
                ORDER BY $orderByClause
                LIMIT :limit OFFSET :offset
            ) sub
        """

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("studyId", studyId)
        query.setParameter("limit", limit)
        query.setParameter("offset", offset)
        if (search != null) query.setParameter("search", search)

        return query.singleResult as? String
    }

    @Suppress("LongParameterList")
    override fun queryParticipantAccounts(
        studyId: String,
        offset: Int?,
        limit: Int?,
        search: String?,
        isDeployed: Boolean?,
        sortDirection: SortDirection?,
        sortBy: ParticipantOrderBy?,
    ): List<ParticipantAccountQueryRow> {
        val paginationClause = if (limit != null && offset != null) "LIMIT :limit OFFSET :offset" else ""

        val sql = """
            $DEPLOYED_PARTICIPANTS_CTE
            SELECT
                (${participantJson("p")})::text AS participant,
                dp.participant_id IS NOT NULL AS is_deployed,
                dp.deployment_id
            FROM recruitment_participants p
            LEFT JOIN deployed_participants dp ON dp.participant_id = p.participant_id
            WHERE p.study_id = :studyId
            ${buildSearchCondition("p", search)}
            ${buildDeploymentCondition(isDeployed)}
            ORDER BY ${accountOrderBy(sortBy, sortDirection)}
            $paginationClause
        """

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("studyId", studyId)
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
                deploymentId = row[2] as String?,
            )
        }
    }

    override fun countQueryParticipantAccounts(
        studyId: String,
        search: String?,
        isDeployed: Boolean?,
    ): Int {
        val sql = """
            $DEPLOYED_PARTICIPANTS_CTE
            SELECT COUNT(*)
            FROM recruitment_participants p
            LEFT JOIN deployed_participants dp ON dp.participant_id = p.participant_id
            WHERE p.study_id = :studyId
            ${buildSearchCondition("p", search)}
            ${buildDeploymentCondition(isDeployed)}
        """

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("studyId", studyId)
        if (search != null) query.setParameter("search", search)

        return (query.singleResult as Number).toInt()
    }

    /** ORDER BY for the account query; mirrors the previous JSONB ordering (deployed, identity, stable). */
    private fun accountOrderBy(
        sortBy: ParticipantOrderBy?,
        sortDirection: SortDirection?,
    ): String {
        val direction = if (sortDirection == SortDirection.Desc) "DESC" else "ASC"
        return when {
            sortBy == ParticipantOrderBy.IsDeployed ->
                "CASE WHEN dp.participant_id IS NULL THEN 0 ELSE 1 END $direction, " +
                    "COALESCE(p.username, p.email_address) $direction, p.sort_order $direction"
            sortBy == ParticipantOrderBy.AccountIdentity || sortDirection != null ->
                "COALESCE(p.username, p.email_address) $direction"
            else -> "p.sort_order"
        }
    }

    private fun buildSearchCondition(
        alias: String,
        search: String?,
    ): String =
        if (search != null) {
            """
            AND (
                $alias.participant_id ILIKE CONCAT('%', :search, '%')
                OR $alias.username ILIKE CONCAT('%', :search, '%')
                OR $alias.email_address ILIKE CONCAT('%', :search, '%')
            )
            """
        } else {
            ""
        }

    private fun buildDeploymentCondition(isDeployed: Boolean?): String =
        when (isDeployed) {
            true -> "AND dp.participant_id IS NOT NULL"
            false -> "AND dp.participant_id IS NULL"
            null -> ""
        }
}
