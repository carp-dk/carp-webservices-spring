package dk.cachet.carp.webservices.datastream.service

import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.data.application.DataStreamsConfiguration
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.repository.DataStreamConfigurationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.jackson.databind.JsonNode

suspend fun validateConfig(
    dataStream: DataStreamId,
    configRepository: DataStreamConfigurationRepository,
) {
    val config =
        withContext(Dispatchers.IO) {
            configRepository.findById(dataStream.studyDeploymentId.stringRepresentation)
        }.orElseThrow {
            IllegalArgumentException(
                "No configuration found for studyDeploymentId " +
                    "${dataStream.studyDeploymentId} or the study is closed.",
            )
        }.let {
            mapToCoreConfig(it.config!!)
        }

    require(dataStream in config.expectedDataStreamIds) {
        "Data stream not configured for studyDeploymentId ${dataStream.studyDeploymentId.stringRepresentation}"
    }
}

private fun mapToCoreConfig(node: JsonNode) =
    WS_JSON.decodeFromString(
        DataStreamsConfiguration.serializer(),
        node.toString(),
    )
