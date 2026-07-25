package dk.cachet.carp.webservices.study.domain.normalization

import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Types

/**
 * JdbcTemplate-backed persistence for the normalized recruitment tables (`recruitment_participants`,
 * `recruitment_participant_groups`, `recruitment_participant_group_members`). No JPA entities — the
 * codebase persists bulk recruitment data via JdbcTemplate and reads it via native SQL, and this
 * follows that convention. Callers manage the transaction boundary.
 */
@Component
class RecruitmentNormalizationStore(
    private val jdbcTemplate: JdbcTemplate,
) {
    /** Idempotently replace all normalized rows for [recruitmentId] with [normalized]'s rows. */
    fun replace(
        recruitmentId: Int,
        normalized: NormalizedRecruitment,
    ) {
        // Deleting groups cascades to members (FK ON DELETE CASCADE); participants are independent.
        jdbcTemplate.update("DELETE FROM recruitment_participant_groups WHERE recruitment_id = ?", recruitmentId)
        jdbcTemplate.update("DELETE FROM recruitment_participants WHERE recruitment_id = ?", recruitmentId)

        insertParticipants(recruitmentId, normalized)
        insertGroups(recruitmentId, normalized)
        insertMembers(normalized.studyId, normalized.members)
    }

    /** Read the persisted normalized rows for [recruitmentId] (for verification / reconstruction). */
    fun readRows(recruitmentId: Int): RecruitmentRows {
        val participants =
            jdbcTemplate.query(
                "SELECT participant_id, account_identity_type, username, email_address, sort_order " +
                    "FROM recruitment_participants WHERE recruitment_id = ? ORDER BY sort_order",
                { rs, _ ->
                    RecruitmentParticipantRow(
                        participantId = rs.getString("participant_id"),
                        accountIdentityType = rs.getString("account_identity_type"),
                        username = rs.getString("username"),
                        emailAddress = rs.getString("email_address"),
                        sortOrder = rs.getInt("sort_order"),
                    )
                },
                recruitmentId,
            )

        val groups =
            jdbcTemplate.query(
                "SELECT group_id, is_deployed, name FROM recruitment_participant_groups WHERE recruitment_id = ?",
                { rs, _ ->
                    RecruitmentGroupRow(
                        groupId = rs.getString("group_id"),
                        isDeployed = rs.getBoolean("is_deployed"),
                        name = rs.getString("name"),
                    )
                },
                recruitmentId,
            )

        val members =
            jdbcTemplate.query(
                "SELECT m.group_id, m.participant_id, m.assigned_all, m.role_names " +
                    "FROM recruitment_participant_group_members m " +
                    "JOIN recruitment_participant_groups g ON m.group_id = g.group_id " +
                    "WHERE g.recruitment_id = ?",
                { rs, _ ->
                    val rawArray = rs.getArray("role_names")
                    @Suppress("UNCHECKED_CAST")
                    val roleNames = (rawArray?.array as? Array<String>)?.toList()
                    RecruitmentGroupMemberRow(
                        groupId = rs.getString("group_id"),
                        participantId = rs.getString("participant_id"),
                        assignedAll = rs.getBoolean("assigned_all"),
                        roleNames = roleNames,
                    )
                },
                recruitmentId,
            )

        return RecruitmentRows(participants, groups, members)
    }

    @Suppress("MagicNumber") // JDBC positional parameter indices
    private fun insertParticipants(
        recruitmentId: Int,
        normalized: NormalizedRecruitment,
    ) {
        val rows = normalized.participants
        jdbcTemplate.batchUpdate(
            "INSERT INTO recruitment_participants " +
                "(recruitment_id, study_id, participant_id, account_identity_type, username, email_address, " +
                "sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now())",
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: PreparedStatement,
                    i: Int,
                ) {
                    val row = rows[i]
                    ps.setInt(1, recruitmentId)
                    ps.setString(2, normalized.studyId)
                    ps.setString(3, row.participantId)
                    ps.setString(4, row.accountIdentityType)
                    ps.setString(5, row.username)
                    ps.setString(6, row.emailAddress)
                    ps.setInt(7, row.sortOrder)
                }

                override fun getBatchSize(): Int = rows.size
            },
        )
    }

    @Suppress("MagicNumber") // JDBC positional parameter indices
    private fun insertGroups(
        recruitmentId: Int,
        normalized: NormalizedRecruitment,
    ) {
        val rows = normalized.groups
        jdbcTemplate.batchUpdate(
            "INSERT INTO recruitment_participant_groups " +
                "(recruitment_id, study_id, group_id, is_deployed, name, created_at) VALUES (?, ?, ?, ?, ?, now())",
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: PreparedStatement,
                    i: Int,
                ) {
                    val row = rows[i]
                    ps.setInt(1, recruitmentId)
                    ps.setString(2, normalized.studyId)
                    ps.setString(3, row.groupId)
                    ps.setBoolean(4, row.isDeployed)
                    ps.setString(5, row.name)
                }

                override fun getBatchSize(): Int = rows.size
            },
        )
    }

    @Suppress("MagicNumber") // JDBC positional parameter indices
    private fun insertMembers(
        studyId: String,
        rows: List<RecruitmentGroupMemberRow>,
    ) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO recruitment_participant_group_members " +
                "(study_id, group_id, participant_id, assigned_all, role_names) VALUES (?, ?, ?, ?, ?)",
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: PreparedStatement,
                    i: Int,
                ) {
                    val row = rows[i]
                    ps.setString(1, studyId)
                    ps.setString(2, row.groupId)
                    ps.setString(3, row.participantId)
                    ps.setBoolean(4, row.assignedAll)
                    val roleNames = row.roleNames
                    if (roleNames == null) {
                        ps.setNull(5, Types.ARRAY)
                    } else {
                        ps.setArray(5, ps.connection.createArrayOf("text", roleNames.toTypedArray()))
                    }
                }

                override fun getBatchSize(): Int = rows.size
            },
        )
    }
}
