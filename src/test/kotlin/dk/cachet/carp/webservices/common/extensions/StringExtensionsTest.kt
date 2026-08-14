package dk.cachet.carp.webservices.common.extensions

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

/**
 * Locks the case conversions used to map snake_case query parameters onto JPA property names.
 *
 * [toCamelCase] replaced Guava's `CaseFormat.LOWER_UNDERSCORE.to(LOWER_CAMEL, ...)`; these cases
 * pin the behaviour that replacement has to keep.
 */
class StringExtensionsTest {
    @Test
    fun `toCamelCase converts snake case to camel case`() {
        assertEquals("fooBar", "foo_bar".toCamelCase())
        assertEquals("fooBarBaz", "foo_bar_baz".toCamelCase())
    }

    @Test
    fun `toCamelCase leaves a single lowercase word untouched`() {
        assertEquals("foo", "foo".toCamelCase())
    }

    @Test
    fun `toCamelCase lowercases uppercase input`() {
        assertEquals("fooBar", "FOO_BAR".toCamelCase())
    }

    @Test
    fun `toCamelCase collapses empty segments`() {
        assertEquals("fooBar", "foo__bar".toCamelCase())
        assertEquals("fooBar", "_foo_bar".toCamelCase())
    }

    @Test
    fun `toCamelCase returns empty string for empty input`() {
        assertEquals("", "".toCamelCase())
    }

    @Test
    fun `toCamelCase round trips with toSnakeCase`() {
        assertEquals("deploymentId", "deploymentId".toSnakeCase().toCamelCase())
        assertEquals("studyDeploymentId", "studyDeploymentId".toSnakeCase().toCamelCase())
    }
}
