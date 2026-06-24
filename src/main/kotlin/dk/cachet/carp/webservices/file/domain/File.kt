package dk.cachet.carp.webservices.file.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@Entity(name = "files")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class File(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    // (storageName)
    @field:NotNull
    var fileName: String = "",
    // relative path e.g. .../local/{relativePath}/{fileName}
    @field:NotNull
    val relativePath: String = "",
    @field:NotNull
    var originalName: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var metadata: JsonNode? = null,
    @field:NotNull
    var studyId: String = "",
    var ownerId: String? = null,
    var deploymentId: String? = null,
) : Auditable()
