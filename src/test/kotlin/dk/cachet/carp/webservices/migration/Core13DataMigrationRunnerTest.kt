package dk.cachet.carp.webservices.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Core13DataMigrationRunnerTest {
    @Test
    fun `deployment batch query only contains placeholders for after ID and batch size`() {
        val query = deploymentBatchQuery(legacyOnly = true)

        assertEquals(2, query.count { it == '?' })
        assertTrue(query.contains(LEGACY_DEPLOYMENT_PREDICATE))
        assertFalse(query.contains("snapshot ? 'isStopped'"))
    }
}
