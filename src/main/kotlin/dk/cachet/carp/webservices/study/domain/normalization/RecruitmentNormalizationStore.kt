package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
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
    /**
     * Synchronize the recruitment's normalized rows to [normalized] by applying only the delta against
     * what is currently stored — inserting new rows, deleting removed ones, updating changed ones. This
     * keeps a single-participant change to a single-row write instead of rewriting the whole recruitment.
     * Backfill of an empty recruitment inserts everything; a re-run with no changes is a no-op. Idempotent.
     */
    fun replace(
        recruitmentId: Int,
        normalized: NormalizedRecruitment,
    ) {
        val current = readRows(recruitmentId)
        // Order matters for the FKs: participants and groups exist before members reference them;
        // removing a participant/group cascades its members (any explicit member delete is then a no-op).
        syncParticipants(recruitmentId, normalized.studyId, current.participants, normalized.participants)
        syncGroups(recruitmentId, normalized.studyId, current.groups, normalized.groups)
        syncMembers(normalized.studyId, current.members, normalized.members)
    }

    private fun syncParticipants(
        recruitmentId: Int,
        studyId: String,
        current: List<RecruitmentParticipantRow>,
        desired: List<RecruitmentParticipantRow>,
    ) {
        val currentByKey = current.associateBy { it.participantId }
        val desiredByKey = desired.associateBy { it.participantId }
        deleteParticipants(recruitmentId, (currentByKey.keys - desiredByKey.keys).toList())
        insertParticipants(recruitmentId, studyId, desired.filter { it.participantId !in currentByKey })
        val changed = desired.filter { it.participantId in currentByKey && currentByKey[it.participantId] != it }
        updateParticipants(recruitmentId, changed)
    }

    private fun syncGroups(
        recruitmentId: Int,
        studyId: String,
        current: List<RecruitmentGroupRow>,
        desired: List<RecruitmentGroupRow>,
    ) {
        val currentByKey = current.associateBy { it.groupId }
        val desiredByKey = desired.associateBy { it.groupId }
        deleteGroups(recruitmentId, (currentByKey.keys - desiredByKey.keys).toList())
        insertGroups(recruitmentId, studyId, desired.filter { it.groupId !in currentByKey })
        val changed = desired.filter { it.groupId in currentByKey && currentByKey[it.groupId] != it }
        updateGroups(recruitmentId, changed)
    }

    private fun syncMembers(
        studyId: String,
        current: List<RecruitmentGroupMemberRow>,
        desired: List<RecruitmentGroupMemberRow>,
    ) {
        val key = { m: RecruitmentGroupMemberRow -> m.groupId to m.participantId }
        val currentByKey = current.associateBy(key)
        val desiredByKey = desired.associateBy(key)
        deleteMembers((currentByKey.keys - desiredByKey.keys).toList())
        insertMembers(studyId, desired.filter { key(it) !in currentByKey })
        val changed = desired.filter { key(it) in currentByKey && currentByKey[key(it)] != it }
        updateMembers(changed)
    }

    /**
     * Append [participants] and [groups] to an existing recruitment's tables without touching existing
     * rows (the relational equivalent of the JSONB bulk-append). `sort_order` continues after the current
     * maximum so appended participants sort after existing ones.
     *
     * Must run inside a transaction (see class doc): this locks the parent `recruitments` row for the
     * duration of that transaction (via [lockAndGetVersion]), so concurrent appends for the SAME
     * recruitment (e.g. a burst of self-signup requests for one study) serialize instead of racing on the
     * MAX(sort_order) read below - under READ COMMITTED, two concurrent transactions could otherwise both
     * read the same max and insert different participants with an identical sort_order. A blocked caller
     * simply waits for the lock holder to commit, after which it sees the up-to-date max; the lock
     * releases at commit/rollback.
     *
     * Also bumps the recruitment's persisted version (see [lockAndGetVersion]/[bumpVersion]): this append
     * IS an edit to the recruitment, and [CoreParticipantRepository][dk.cachet.carp.webservices.study.repository.CoreParticipantRepository.updateRecruitment]
     * relies on that version to detect a since-modified recruitment and refuse to overwrite it - without
     * this, an unrelated concurrent command's stale `replace()` would silently DELETE the rows appended
     * here, since its desired-state snapshot has no way of knowing about them.
     */
    fun append(
        recruitmentId: Int,
        studyId: String,
        participants: Collection<Participant>,
        groups: Map<UUID, StagedParticipantGroup>,
    ) {
        val currentVersion = lockAndGetVersion(recruitmentId)
        bumpVersion(recruitmentId, currentVersion + 1)

        val nextSortOrder =
            jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM recruitment_participants WHERE recruitment_id = ?",
                Int::class.java,
                recruitmentId,
            ) ?: 0
        insertParticipants(recruitmentId, studyId, RecruitmentNormalizer.participantRows(participants, nextSortOrder))
        insertGroups(recruitmentId, studyId, RecruitmentNormalizer.groupRows(groups))
        insertMembers(studyId, RecruitmentNormalizer.memberRows(groups))
    }

    /**
     * Lock [recruitmentId]'s row for the remainder of the caller's transaction and return the version
     * currently embedded in its JSONB snapshot envelope - the same value carp.core's aggregates track as
     * `AggregateRoot.fromSnapshotVersion` (see `Snapshot.version`: "the number of edits made to the object
     * represented by this snapshot"). Every writer of this recruitment (this class's [append], and
     * `CoreParticipantRepository.updateRecruitment`) must call this FIRST, inside the same transaction as
     * its write, so a concurrent writer for the same recruitment blocks until this transaction commits -
     * that ordering is what makes the version comparison in `updateRecruitment` race-free rather than a
     * check-then-act gap.
     */
    fun lockAndGetVersion(recruitmentId: Int): Int =
        checkNotNull(
            jdbcTemplate.queryForObject(
                "SELECT (snapshot->>'version')::int FROM recruitments WHERE id = ? FOR UPDATE",
                Int::class.java,
                recruitmentId,
            ),
        ) { "Recruitment $recruitmentId has no snapshot; it should already exist by the time this is called." }

    /**
     * Advance [recruitmentId]'s persisted version to [newVersion]. Only [append] needs this: a
     * carp.core-driven `replace()` (via `updateRecruitment`) writes its own new version as part of the
     * normal snapshot it saves, but `append()` writes directly to the normalized child tables and never
     * touches the snapshot otherwise, so without this its edit would be invisible to a concurrent
     * version check.
     */
    private fun bumpVersion(
        recruitmentId: Int,
        newVersion: Int,
    ) {
        jdbcTemplate.update(
            "UPDATE recruitments SET snapshot = jsonb_set(snapshot, '{version}', to_jsonb(?)) WHERE id = ?",
            newVersion,
            recruitmentId,
        )
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

                    // Sorted to match the canonical order written by the normalizer (see memberRows),
                    // so the write-path diff compares equal for unchanged role assignments.
                    @Suppress("UNCHECKED_CAST")
                    val roleNames = (rawArray?.array as? Array<String>)?.toList()?.sorted()
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
        studyId: String,
        rows: List<RecruitmentParticipantRow>,
    ) {
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
                    ps.setString(2, studyId)
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
        studyId: String,
        rows: List<RecruitmentGroupRow>,
    ) {
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
                    ps.setString(2, studyId)
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

    private fun deleteParticipants(
        recruitmentId: Int,
        participantIds: List<String>,
    ) = batchByKey(
        "DELETE FROM recruitment_participants WHERE recruitment_id = ? AND participant_id = ?",
        participantIds,
    ) { ps, id ->
        ps.setInt(1, recruitmentId)
        ps.setString(2, id)
    }

    private fun deleteGroups(
        recruitmentId: Int,
        groupIds: List<String>,
    ) = batchByKey(
        "DELETE FROM recruitment_participant_groups WHERE recruitment_id = ? AND group_id = ?",
        groupIds,
    ) { ps, id ->
        ps.setInt(1, recruitmentId)
        ps.setString(2, id)
    }

    private fun deleteMembers(keys: List<Pair<String, String>>) =
        batchByKey(
            "DELETE FROM recruitment_participant_group_members WHERE group_id = ? AND participant_id = ?",
            keys,
        ) { ps, (groupId, participantId) ->
            ps.setString(1, groupId)
            ps.setString(2, participantId)
        }

    @Suppress("MagicNumber") // JDBC positional parameter indices
    private fun updateParticipants(
        recruitmentId: Int,
        rows: List<RecruitmentParticipantRow>,
    ) = batchByKey(
        "UPDATE recruitment_participants SET account_identity_type = ?, username = ?, email_address = ?, " +
            "sort_order = ?, updated_at = now() WHERE recruitment_id = ? AND participant_id = ?",
        rows,
    ) { ps, row ->
        ps.setString(1, row.accountIdentityType)
        ps.setString(2, row.username)
        ps.setString(3, row.emailAddress)
        ps.setInt(4, row.sortOrder)
        ps.setInt(5, recruitmentId)
        ps.setString(6, row.participantId)
    }

    @Suppress("MagicNumber") // JDBC positional parameter indices
    private fun updateGroups(
        recruitmentId: Int,
        rows: List<RecruitmentGroupRow>,
    ) = batchByKey(
        "UPDATE recruitment_participant_groups SET is_deployed = ?, name = ?, updated_at = now() " +
            "WHERE recruitment_id = ? AND group_id = ?",
        rows,
    ) { ps, row ->
        ps.setBoolean(1, row.isDeployed)
        ps.setString(2, row.name)
        ps.setInt(3, recruitmentId)
        ps.setString(4, row.groupId)
    }

    @Suppress("MagicNumber") // JDBC positional parameter indices
    private fun updateMembers(rows: List<RecruitmentGroupMemberRow>) =
        batchByKey(
            "UPDATE recruitment_participant_group_members SET assigned_all = ?, role_names = ? " +
                "WHERE group_id = ? AND participant_id = ?",
            rows,
        ) { ps, row ->
            ps.setBoolean(1, row.assignedAll)
            val roleNames = row.roleNames
            if (roleNames == null) {
                ps.setNull(2, Types.ARRAY)
            } else {
                ps.setArray(2, ps.connection.createArrayOf("text", roleNames.toTypedArray()))
            }
            ps.setString(3, row.groupId)
            ps.setString(4, row.participantId)
        }

    /** Batch a single prepared statement over [items]; a no-op when empty. */
    private fun <T> batchByKey(
        sql: String,
        items: List<T>,
        bind: (PreparedStatement, T) -> Unit,
    ) {
        if (items.isEmpty()) return
        jdbcTemplate.batchUpdate(
            sql,
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: PreparedStatement,
                    i: Int,
                ) = bind(ps, items[i])

                override fun getBatchSize(): Int = items.size
            },
        )
    }
}
