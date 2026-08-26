package dk.cachet.carp.webservices.selfsignup.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortCodeGeneratorTest {
    @Test
    fun `generates a 5-letter uppercase code excluding ambiguous letters`() {
        repeat(1000) {
            val code = ShortCodeGenerator.generate()

            assertEquals(5, code.length)
            assertTrue(code.all { it in 'A'..'Z' })
            assertTrue(code.none { it in "IOQ" })
        }
    }

    @Test
    fun `generated codes are not all identical`() {
        val codes = (1..100).map { ShortCodeGenerator.generate() }.toSet()

        assertTrue(codes.size > 1)
    }
}
