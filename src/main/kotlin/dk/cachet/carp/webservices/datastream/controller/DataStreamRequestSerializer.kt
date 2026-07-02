package dk.cachet.carp.webservices.datastream.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.services.ApplicationService
import dk.cachet.carp.common.infrastructure.services.ApplicationServiceRequest
import dk.cachet.carp.data.application.DataStreamBatch
import dk.cachet.carp.data.infrastructure.DataStreamServiceRequest
import dk.cachet.carp.webservices.common.serialisers.ApplicationRequestSerializer
import kotlinx.serialization.serializer

/**
 * Serializes and deserializes [DataStreamServiceRequest] to and from the application service.
 * This is used to deserialize requests to the application service and serialize responses from the application service.
 */
class DataStreamRequestSerializer : ApplicationRequestSerializer<DataStreamServiceRequest<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <TService : ApplicationService<TService, *>> serializeResponse(
        request: ApplicationServiceRequest<TService, *>,
        content: Any?,
    ): Any? {
        return when (request) {
            is DataStreamServiceRequest.GetDataStream ->
                json.encodeToString(DataStreamBatch.serializer(), content as DataStreamBatch)
            is DataStreamServiceRequest.OpenDataStreams,
            is DataStreamServiceRequest.AppendToDataStreams,
            is DataStreamServiceRequest.CloseDataStreams,
            -> json.encodeToString(serializer<Unit>(), content as Unit)
            is DataStreamServiceRequest.RemoveDataStreams ->
                json.encodeToString(serializer<Set<UUID>>(), content as Set<UUID>)
            else -> content
        }
    }
}
