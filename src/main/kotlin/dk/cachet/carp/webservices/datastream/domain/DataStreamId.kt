package dk.cachet.carp.webservices.datastream.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.io.Serializable

@Entity(name = "data_stream_ids")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DataStreamId(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    var studyDeploymentId: String? = "",
    var deviceRoleName: String? = "",
    var name: String? = "",
    var nameSpace: String? = "",
) : Auditable(), Serializable
