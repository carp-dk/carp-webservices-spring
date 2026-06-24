package dk.cachet.carp.webservices.collection.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import dk.cachet.carp.webservices.document.domain.Document
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.DynamicUpdate
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.io.Serializable

@DynamicUpdate
@Entity(name = "collections")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Collection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    @field:NotNull
    var name: String = "",
    @field:NotNull
    var studyId: String = "",
    var studyDeploymentId: String? = "",
    val documentId: Int? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "collectionId")
    @OrderBy("createdAt desc")
    var documents: List<Document>? = null,
) : Auditable(), Serializable
