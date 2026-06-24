package dk.cachet.carp.webservices.datastream.service

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.data.application.MutableDataStreamSequence
import dk.cachet.carp.webservices.datastream.domain.DataStreamSequence
import dk.cachet.carp.webservices.datastream.domain.DataStreamSnapshot
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

fun createSequence(
    dataStream: DataStreamId,
    sequence: DataStreamSequence,
    subRange: LongRange,
    objectMapper: ObjectMapper,
): MutableDataStreamSequence<Data> {
    val snapshot: DataStreamSnapshot = mapToDataStreamSnapshot(sequence.snapshot!!, objectMapper)
    val startOffset = (subRange.first - sequence.firstSequenceId!!).toInt()
    val exclusiveEnd = (startOffset + (subRange.last - subRange.first + 1)).toInt()

    require(startOffset >= 0 && exclusiveEnd >= 0) {
        "Measurement indices must not be negative."
    }

    return MutableDataStreamSequence<Data>(
        dataStream,
        subRange.first,
        snapshot.triggerIds,
        snapshot.syncPoint,
    ).apply {
        appendMeasurements(snapshot.measurements.subList(startOffset, exclusiveEnd))
    }
}

private fun mapToDataStreamSnapshot(
    node: JsonNode,
    objectMapper: ObjectMapper,
): DataStreamSnapshot {
    return objectMapper.treeToValue(node, DataStreamSnapshot::class.java)
}
