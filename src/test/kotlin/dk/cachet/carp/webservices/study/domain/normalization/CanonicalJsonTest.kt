package dk.cachet.carp.webservices.study.domain.normalization

import dk.cachet.carp.webservices.common.input.WS_JSON
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression coverage for the verify-comparison bug: sorting array elements by `toString()` is only
 * correct when object keys are also normalized. A stored blob's key order (Postgres jsonb) differs
 * from current kotlinx serialization, so without key-sorting a multi-element array of equal objects
 * sorted differently on each side and comparison spuriously failed.
 */
class CanonicalJsonTest {
    private fun canonical(json: String) = CanonicalJson.canonicalize(WS_JSON.parseToJsonElement(json))

    @Test
    fun `equal object multiset with different key order and array order is canonically equal`() {
        val a = """[{"a":"1","b":"2"},{"a":"2","b":"1"}]"""
        val b = """[{"b":"1","a":"2"},{"b":"2","a":"1"}]"""
        assertEquals(canonical(a), canonical(b))
    }

    @Test
    fun `nested object key order does not affect equality`() {
        val a = """{"outer":{"x":1,"y":2},"list":[{"p":"1","q":"2"}]}"""
        val b = """{"list":[{"q":"2","p":"1"}],"outer":{"y":2,"x":1}}"""
        assertEquals(canonical(a), canonical(b))
    }

    @Test
    fun `genuinely different values are not equal`() {
        assertNotEquals(canonical("""{"a":1}"""), canonical("""{"a":2}"""))
        assertNotEquals(canonical("""[1,2]"""), canonical("""[1,2,3]"""))
    }
}
