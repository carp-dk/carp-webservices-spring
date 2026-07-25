package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * Local-only validation of [RecruitmentNormalizer] against real recruitment snapshots.
 *
 * Guarded by the `normalizer.realdata.file` system property (a TSV of `id<TAB>snapshotJson` per line,
 * dumped from a database); skipped when unset, so it never runs in CI. Proves that every *decodable*
 * snapshot round-trips losslessly, and reports (without failing) snapshots the current `WS_JSON`
 * cannot decode — those are pre-existing data-quality issues (e.g. stale `type` discriminator), which
 * the backfill runner must record and skip rather than crash on.
 */
class RecruitmentNormalizerRealDataTest {
    @Test
    fun `all decodable real recruitment snapshots round-trip losslessly`() {
        val path = System.getProperty("normalizer.realdata.file")
        assumeTrue(path != null, "normalizer.realdata.file not set; skipping real-data validation")

        var decoded = 0
        var decodeFailed = 0
        var roundTripOk = 0
        val roundTripMismatches = mutableListOf<String>()
        val decodeFailures = mutableListOf<String>()

        File(path!!).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val tab = line.indexOf('\t')
            val id = line.substring(0, tab)
            val json = line.substring(tab + 1)

            val snapshot =
                try {
                    WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), json)
                } catch (e: Exception) {
                    decodeFailed++
                    decodeFailures += "id=$id: ${e.message?.take(140)}"
                    return@forEachLine
                }
            decoded++

            val roundTripped = RecruitmentNormalizer.reconstruct(RecruitmentNormalizer.decompose(snapshot))
            if (canonical(encode(snapshot)) == canonical(encode(roundTripped))) {
                roundTripOk++
            } else {
                roundTripMismatches += "id=$id"
            }
        }

        println("=== real-data normalizer validation ===")
        val mismatchCount = roundTripMismatches.size
        println("decoded=$decoded failed=$decodeFailed roundTripOk=$roundTripOk mismatches=$mismatchCount")
        if (decodeFailures.isNotEmpty()) {
            println("decode failures (pre-existing data issues):\n  " + decodeFailures.joinToString("\n  "))
        }
        if (roundTripMismatches.isNotEmpty()) {
            println("ROUND-TRIP MISMATCHES:\n  " + roundTripMismatches.joinToString("\n  "))
        }

        assertEquals(
            emptyList(),
            roundTripMismatches,
            "every decodable snapshot must round-trip losslessly through the normalizer",
        )
    }

    private fun encode(snapshot: RecruitmentSnapshot): String =
        WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), snapshot)

    private fun canonical(json: String): JsonElement = canonicalize(WS_JSON.parseToJsonElement(json))

    private fun canonicalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
            is JsonArray -> JsonArray(element.map(::canonicalize).sortedBy { it.toString() })
            else -> element
        }
}
