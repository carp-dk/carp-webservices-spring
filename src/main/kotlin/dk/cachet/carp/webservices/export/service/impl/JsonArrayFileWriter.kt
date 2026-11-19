package dk.cachet.carp.webservices.export.service.impl

import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.encodeToStream
import tools.jackson.databind.ObjectMapper
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes a JSON array to [path] one element at a time, so an export never needs all of its elements in memory.
 * [close] completes the array; [abort] discards the partially written file.
 */
internal sealed class JsonArrayFileWriter(
    private val path: Path,
) : Closeable {
    abstract fun write(element: Any?)

    fun abort() {
        runCatching { close() }
        Files.deleteIfExists(path)
    }
}

/** Writes CARP Core types with [WS_JSON], the same serializer used to store them and to serve the API. */
internal class KotlinxJsonArrayFileWriter(
    path: Path,
    serializer: KSerializer<*>,
) : JsonArrayFileWriter(path) {
    @Suppress("UNCHECKED_CAST")
    private val serializer = serializer as KSerializer<Any?>
    private val output = Files.newOutputStream(path).buffered()
    private var isFirst = true

    init {
        output.write('['.code)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun write(element: Any?) {
        if (!isFirst) output.write(','.code)
        isFirst = false
        WS_JSON.encodeToStream(serializer, element, output)
    }

    override fun close() = output.use { it.write(']'.code) }
}

/** Writes web-services domain types (Jackson-annotated entities) with Jackson. */
internal class JacksonJsonArrayFileWriter(
    path: Path,
    private val objectMapper: ObjectMapper,
) : JsonArrayFileWriter(path) {
    private val generator = objectMapper.createGenerator(Files.newOutputStream(path))

    init {
        generator.writeStartArray()
    }

    override fun write(element: Any?) = objectMapper.writeValue(generator, element)

    override fun close() {
        generator.use { it.writeEndArray() }
    }
}
