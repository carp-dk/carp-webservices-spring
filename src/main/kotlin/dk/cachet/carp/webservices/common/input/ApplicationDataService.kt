package dk.cachet.carp.webservices.common.input

import dk.cachet.carp.common.application.ApplicationData
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

@Service
class ApplicationDataService(
    private val objectMapper: ObjectMapper,
) {
    fun mergeApplicationData(
        existingData: ApplicationData?,
        fieldsToMerge: Map<String, String>,
    ): ApplicationData {
        fun createMergedNode() =
            objectMapper
                .createObjectNode()
                .apply {
                    fieldsToMerge.forEach { (key, value) -> put(key, value) }
                }

        if (existingData?.data.isNullOrBlank()) return ApplicationData(createMergedNode().toString())

        val existingNode = runCatching { objectMapper.readTree(existingData.data) }.getOrNull()
        if (existingNode is ObjectNode) {
            fieldsToMerge.forEach { (key, value) -> existingNode.put(key, value) }
            return ApplicationData(existingNode.toString())
        }

        return ApplicationData(
            createMergedNode()
                .put("legacyApplicationData", existingData.data)
                .toString(),
        )
    }

    fun extractApplicationName(applicationData: ApplicationData?): String? =
        extractApplicationName(applicationData?.data)

    fun extractApplicationName(applicationData: String?): String? {
        if (applicationData.isNullOrBlank()) return null
        val applicationDataNode = runCatching { objectMapper.readTree(applicationData) }.getOrNull() ?: return null
        return applicationDataNode.path("applicationName").asString().takeIf { it.isNotBlank() }
    }
}
