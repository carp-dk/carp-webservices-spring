package dk.cachet.carp.webservices.common.input

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service

@Service
class ApplicationDataService(
    private val objectMapper: ObjectMapper,
) {
    fun mergeApplicationData(
        existingData: String?,
        fieldsToMerge: Map<String, String>,
    ): String {
        fun createMergedNode() =
            objectMapper
                .createObjectNode()
                .apply {
                    fieldsToMerge.forEach { (key, value) -> put(key, value) }
                }

        if (existingData.isNullOrBlank()) return createMergedNode().toString()

        val existingNode = runCatching { objectMapper.readTree(existingData) }.getOrNull()
        if (existingNode is ObjectNode) {
            fieldsToMerge.forEach { (key, value) -> existingNode.put(key, value) }
            return existingNode.toString()
        }

        return createMergedNode()
            .put("legacyApplicationData", existingData)
            .toString()
    }

    fun extractApplicationName(applicationData: String?): String? {
        if (applicationData.isNullOrBlank()) return null
        val applicationDataNode = runCatching { objectMapper.readTree(applicationData) }.getOrNull() ?: return null
        return applicationDataNode.path("applicationName").asText().takeIf { it.isNotBlank() }
    }
}
