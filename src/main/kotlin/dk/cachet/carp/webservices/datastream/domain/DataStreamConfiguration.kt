package dk.cachet.carp.webservices.datastream.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@Entity(name = "data_stream_configurations")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DataStreamConfiguration(
    @Id
    var studyDeploymentId: String? = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var config: JsonNode? = null,
    var closed: Boolean = false,
)
