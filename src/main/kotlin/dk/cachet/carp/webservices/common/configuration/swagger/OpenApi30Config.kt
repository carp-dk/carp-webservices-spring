package dk.cachet.carp.webservices.common.configuration.swagger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.BeanUtils
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.StreamUtils
import org.springframework.util.StringUtils
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList

// https://stackoverflow.com/q/59898874/13179591
// https://stackoverflow.com/a/73622024/13179591
@Configuration
class OpenApi30Config(
    @Value("\${spring.application.name}") private val moduleName: String,
    @Value("\${spring.application.version}") private val apiVersion: String,
    @Value("classpath:openapi/description.txt") private val docResource: Resource,
    private val objectMapper: ObjectMapper,
    private val environmentUtil: EnvironmentUtil,
) {
    companion object {
        const val SCHEME = "bearer"
        const val FORMAT = "JWT"
        const val DESCRIPTION =
            "Provide the username and password of the user"
        const val OPENAPI_FOLDER = "/openapi"
    }

    @Bean
    fun customOpenAPI(): OpenAPI? {
        val apiTitle = String.format(Locale.getDefault(), "%s API", StringUtils.capitalize(moduleName))

        return OpenAPI()
            .addServersItem(Server().url(environmentUtil.url))
            .addSecurityItem(SecurityRequirement().addList(SCHEME))
            .components(
                Components()
                    .addSecuritySchemes(
                        SCHEME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.OAUTH2)
                            .scheme(SCHEME)
                            .bearerFormat(FORMAT)
                            .description(DESCRIPTION)
                            .flows(
                                OAuthFlows().password(
                                    OAuthFlow()
                                        .tokenUrl(
                                            environmentUtil.keycloakUrl
                                                .plus("/realms/")
                                                .plus(environmentUtil.realm)
                                                .plus("/protocol/openid-connect/token")
                                        )
                                )
                            )
                    )
                // Keycloak 17 may fix logout issue with authorizationCode flow till then password flow
//                    .addSecuritySchemes(
//                        SCHEME,
//                        SecurityScheme()
//                            .type(SecurityScheme.Type.OAUTH2)
//                            .flows(
//                                OAuthFlows().authorizationCode(
//                                    OAuthFlow()
//                                        .authorizationUrl(
//                                            "${environmentUtil.keycloakUrl}/realms/
//                                            ${environmentUtil.realm}/protocol/openid-connect/auth"
//                                        )
//                                        .tokenUrl(
//                                            "${environmentUtil.keycloakUrl}/realms/
//                                            ${environmentUtil.realm}/protocol/openid-connect/token"
//                                        )
//                                        .refreshUrl(
//                                            "${environmentUtil.keycloakUrl}/realms/
//                                            ${environmentUtil.realm}/protocol/openid-connect/token"
//                                        )
//                                )
//                            )
//                    )
            )
            .info(
                Info()
                    .title(apiTitle)
                    .version(apiVersion)
                    .description(getResourceContent(docResource)),
            )
    }

    @Bean
    fun openApiCustomizer(loadedOperations: Map<String, Operation>): OpenApiCustomizer {
        return OpenApiCustomizer { openAPI: OpenAPI ->
            for (path in openAPI.paths.values) {
                val operations = listOf(path.post, path.get, path.put, path.delete)

                for (operation in operations) {
                    if (operation == null) continue

                    val loadedOperation = loadedOperations[operation.tags[0]] ?: continue

                    BeanUtils.copyProperties(loadedOperation, operation)

                    // Documentation json files under `resources/openapi/../*.json` overwrites generated content from
                    // annotations in the controllers
                    operation.requestBody = loadedOperation.requestBody
                    operation.responses = loadedOperation.responses
                    operation.parameters = loadedOperation.parameters
                }
            }

            generateAdditionalSchemasForOpenApi(openAPI)
        }
    }

    @Bean
    fun loadOperationsDocumentation(): Map<String, Operation> {
        val loader = PathMatchingResourcePatternResolver()
        val resources = loader.getResources("classpath*:$OPENAPI_FOLDER/*/**/*.json")
        val operations = mutableMapOf<String, Operation>()

        for (resource in resources) {
            val operation = objectMapper.readValue(resource.inputStream, Operation::class.java)
            // keys in the form of 'accounts/inviteStudyOwner.json'
            val key = resource.url.toExternalForm().split('/').takeLast(2).joinToString("/")
            operations[key] = operation
        }

        return operations
    }

    fun getNullPropertyNames(obj: Any): Array<String> {
        val objWrapper: BeanWrapper = BeanWrapperImpl(obj)

        val nullPropertyNames = ArrayList<String>()
        for (descriptor in objWrapper.propertyDescriptors) {
            val value = objWrapper.getPropertyValue(descriptor.name)
            if (value == null) {
                nullPropertyNames.add(descriptor.name)
            }
        }

        return nullPropertyNames.toTypedArray()
    }

    private fun getResourceContent(resource: Resource): String {
        return StreamUtils.copyToString(resource.inputStream, Charset.defaultCharset())
    }

    private fun generateAdditionalSchemasForOpenApi(openAPI: OpenAPI) {
        val loader = PathMatchingResourcePatternResolver()
        val schemaJsonFile = loader.getResources("classpath*:$OPENAPI_FOLDER/schemas.json")

        if (schemaJsonFile.any()) {
            val schemaMap: Map<String, Schema<*>> = objectMapper.readValue(schemaJsonFile[0].inputStream)
            for ((key, schema) in schemaMap) {
                if (!openAPI.components.schemas.containsKey(key)) {
                    openAPI.components.addSchemas(key, schema)
                }
            }
        } else {
            println("Schema file not found.")
        }

        // the json files (resources/openapi/../*.json) reference schemas that might not
        // been previously automatically generated.
        // If that's the case, we need to add them manually (e.g. below)

        //        openAPI.components.addSchemas(
        //            "AddVersion",
        //            ModelConverters.getInstance()
        //                .readAllAsResolvedSchema(ProtocolServiceRequest.AddVersion::class.java).schema,
        //        )
    }
}
