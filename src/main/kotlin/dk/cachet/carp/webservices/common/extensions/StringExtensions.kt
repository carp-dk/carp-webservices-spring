package dk.cachet.carp.webservices.common.extensions

import java.util.*

fun String.toSnakeCase() =
    replace(Regex("([a-z])([A-Z]+)"), "$1_$2")
        .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
        .lowercase(Locale.getDefault())

/**
 * Inverse of [toSnakeCase]: `foo_bar_baz` becomes `fooBarBaz`.
 *
 * Mirrors Guava's `CaseFormat.LOWER_UNDERSCORE.to(LOWER_CAMEL, ...)`, which this replaced — input is
 * lowercased first, so `FOO_BAR` and `foo_bar` both yield `fooBar`, and empty segments collapse.
 */
fun String.toCamelCase() =
    split("_")
        .filter { it.isNotEmpty() }
        .mapIndexed { index, word ->
            val lower = word.lowercase(Locale.getDefault())
            if (index == 0) lower else lower.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }
        .joinToString("")

fun String.toSlug() =
    lowercase(Locale.getDefault())
        .replace("\n", " ")
        .replace("[^a-z\\d\\s]".toRegex(), " ")
        .split(" ")
        .joinToString("-")
        .replace("-+".toRegex(), "-")
