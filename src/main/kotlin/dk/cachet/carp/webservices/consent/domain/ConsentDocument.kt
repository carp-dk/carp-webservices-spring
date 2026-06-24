package dk.cachet.carp.webservices.consent.domain

import com.google.gson.annotations.SerializedName
import dk.cachet.carp.webservices.common.audit.Auditable
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.io.Serializable

/**
 * The Data Class [ConsentDocument].
 * The [ConsentDocument] represents a consent document, with the given [id], [deploymentId], and [data] object.
 */
@Entity(name = "consent_documents")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ConsentDocument(
    /** The consent document [id]. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    /** The [deploymentId] of the consent document. */
    @SerializedName("deployment_id")
    var deploymentId: String? = null,
    /** The [data] object containing the json object parsed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var data: JsonNode? = null,
) : Auditable(), Serializable
