package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * [RecruitmentNormalizationStore.lockAndGetVersion]/[RecruitmentNormalizationStore] read and write a
 * recruitment's version with raw SQL (`(snapshot->>'version')::int` / `jsonb_set(snapshot, '{version}',
 * ...)`) rather than through [WS_JSON]'s typed [RecruitmentSnapshot] round-trip that
 * `CoreParticipantRepository.reconstructFromTables` uses for the same column - a deliberate efficiency
 * trade-off (bumping a counter shouldn't require deserializing the whole snapshot on every self-signup
 * append). This test pins the assumption that raw path depends on: that kotlinx.serialization actually
 * emits the version as a top-level `"version"` key. If a future carp.core upgrade changes
 * `RecruitmentSnapshot`'s serialized shape, this test fails loudly here instead of `lockAndGetVersion`
 * failing an uncaught `IllegalStateException` on every recruitment write in production.
 */
class RecruitmentSnapshotVersionKeyContractTest {
    @Test
    fun `RecruitmentSnapshot serializes its version as a top-level 'version' key`() {
        val snapshot =
            RecruitmentSnapshot(
                id = UUID.randomUUID(),
                studyId = UUID.randomUUID(),
                version = 7,
                studyProtocol = null,
                createdOn = Clock.System.now(),
                invitation = null,
            )

        val json = WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), snapshot)
        val versionInJson = WS_JSON.parseToJsonElement(json).jsonObject["version"]?.jsonPrimitive?.int

        assertEquals(7, versionInJson, "RecruitmentNormalizationStore reads/writes this key with raw SQL")
    }
}
