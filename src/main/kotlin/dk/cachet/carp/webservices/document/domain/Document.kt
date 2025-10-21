package dk.cachet.carp.webservices.document.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import dk.cachet.carp.webservices.SpringApplicationContext
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.common.audit.Auditable
import dk.cachet.carp.webservices.file.service.FileService
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component

/**
 * The Data Class [Document].
 * The [Document] represents a document domain, with the given [id], [name], [collectionId], [collection] and [data].
 */
@DynamicUpdate
@EntityListeners(DocumentListener::class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@Entity(name = "documents")
data class Document(
    /** The document [id]. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    /** The document [name]. */
    @field:NotNull
    var name: String = "",
    /** The document [collectionId] is associated with. */
    @field:NotNull
    var collectionId: Int? = null,
    /** The document [collection] is associated with. */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collectionId", insertable = false, updatable = false)
    var collection: Collection? = null,
    /** The document [collections] is associated with. */
    @JsonIgnore
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "documentId")
    var collections: List<Collection>? = null,
    /** The document [data] object request. */
    @JsonMerge
    @field:JsonMerge
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var data: JsonNode? = null,
) : Auditable()

@Component
@DependsOn("springApplicationContext")
class DocumentListener : InitializingBean {
    companion object {
        private lateinit var fileService: FileService
    }

    override fun afterPropertiesSet() {
        // https://stackoverflow.com/a/28299069/13179591
        fileService = SpringApplicationContext.getBean(FileService::class.java)
    }

    @PostRemove
    fun deleteImageResource(document: Document) {
        if (document.data == null) return

        if (!document.data!!.hasNonNull("image")) return

        val url: String? = document.data!!.get("image").textValue()

        if (url.isNullOrEmpty()) return

        fileService.deleteImage(url)
    }
}
