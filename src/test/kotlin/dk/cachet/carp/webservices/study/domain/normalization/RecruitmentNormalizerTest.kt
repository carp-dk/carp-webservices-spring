package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupRepresentation
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Phase-1 fidelity harness for [RecruitmentNormalizer]: proves that decomposing a core
 * [RecruitmentSnapshot] into normalized rows and reconstructing it is serialization-lossless.
 *
 * Object equality is deliberately NOT used: `StagedParticipantGroup` is a data class whose
 * `equals()` ignores `_roleAssignments`/`isDeployed`, so it cannot detect their loss. We compare
 * the serialized JSON instead, canonicalized so set-valued arrays don't fail on ordering.
 */
class RecruitmentNormalizerTest {
    // ---- fidelity assertion --------------------------------------------------

    private fun assertRoundTripPreservesSnapshot(snapshot: RecruitmentSnapshot) {
        val roundTripped = RecruitmentNormalizer.reconstruct(RecruitmentNormalizer.decompose(snapshot))
        assertEquals(
            canonical(encode(snapshot)),
            canonical(encode(roundTripped)),
            "normalized round-trip changed the serialized RecruitmentSnapshot",
        )
    }

    private fun encode(snapshot: RecruitmentSnapshot): String =
        WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), snapshot)

    /** Sets serialize as arrays in arbitrary order; sort every array so comparison is order-insensitive. */
    private fun canonical(json: String): JsonElement = canonicalize(WS_JSON.parseToJsonElement(json))

    private fun canonicalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
            is JsonArray -> JsonArray(element.map(::canonicalize).sortedBy { it.toString() })
            else -> element
        }

    // ---- fixtures ------------------------------------------------------------

    private val studyId = UUID.randomUUID()
    private val createdOn = Clock.System.now()

    private fun snapshot(
        participants: Set<Participant> = emptySet(),
        groups: Map<UUID, StagedParticipantGroup> = emptyMap(),
        version: Int = 3,
    ) = RecruitmentSnapshot(
        id = UUID.randomUUID(),
        createdOn = createdOn,
        version = version,
        studyId = studyId,
        studyProtocol = null,
        invitation = null,
        participants = participants,
        participantGroups = groups,
    )

    private fun stagedGroup(
        name: String?,
        roles: Set<AssignedParticipantRoles>,
        deployed: Boolean,
    ): StagedParticipantGroup =
        StagedParticipantGroup(UUID.randomUUID(), ParticipantGroupRepresentation(name)).apply {
            if (roles.isNotEmpty()) addParticipants(roles)
            if (deployed) markAsDeployed()
        }

    // ---- tests ---------------------------------------------------------------

    @Test
    fun `empty recruitment round-trips`() {
        assertRoundTripPreservesSnapshot(snapshot())
    }

    @Test
    fun `email participant in a staged AssignedTo-All group round-trips`() {
        val p = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val group = stagedGroup("Group A", setOf(AssignedParticipantRoles(p.id, AssignedTo.All)), deployed = false)
        assertRoundTripPreservesSnapshot(snapshot(setOf(p), mapOf(group.id to group)))
    }

    @Test
    fun `username participant in a deployed AssignedTo-Roles group round-trips`() {
        val p = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val roles = AssignedTo.Roles(setOf("supervisor", "observer"))
        val group = stagedGroup(null, setOf(AssignedParticipantRoles(p.id, roles)), deployed = true)
        assertRoundTripPreservesSnapshot(snapshot(setOf(p), mapOf(group.id to group)))
    }

    @Test
    fun `mixed participants and multiple groups with an unassigned participant round-trips`() {
        val alice = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val bob = Participant(UsernameAccountIdentity("bob"), UUID.randomUUID())
        val carol = Participant(EmailAccountIdentity("carol@example.com"), UUID.randomUUID()) // in no group
        val g1 = stagedGroup("All group", setOf(AssignedParticipantRoles(alice.id, AssignedTo.All)), deployed = false)
        val g2 =
            stagedGroup(
                "Roles group",
                setOf(AssignedParticipantRoles(bob.id, AssignedTo.Roles(setOf("nurse")))),
                deployed = true,
            )
        assertRoundTripPreservesSnapshot(
            snapshot(setOf(alice, bob, carol), mapOf(g1.id to g1, g2.id to g2), version = 7),
        )
    }

    @Test
    fun `decompose stores the envelope with both maps emptied`() {
        val p = Participant(EmailAccountIdentity("alice@example.com"), UUID.randomUUID())
        val group = stagedGroup("Group A", setOf(AssignedParticipantRoles(p.id, AssignedTo.All)), deployed = false)

        val normalized = RecruitmentNormalizer.decompose(snapshot(setOf(p), mapOf(group.id to group)))

        val envelope = WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), normalized.envelopeSnapshot)
        assertTrue(envelope.participants.isEmpty(), "envelope must not carry participants")
        assertTrue(envelope.participantGroups.isEmpty(), "envelope must not carry participant groups")
        assertEquals(1, normalized.participants.size)
        assertEquals(1, normalized.groups.size)
        assertEquals(1, normalized.members.size)
    }
}
