package dk.cachet.carp.webservices.collection.repository

import dk.cachet.carp.webservices.collection.domain.Collection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface CollectionRepository : JpaSpecificationExecutor<Collection>, JpaRepository<Collection, Int> {
    fun findByNameAndStudyIdAndDocumentId(
        name: String,
        studyId: String,
        documentId: Int?,
    ): Optional<Collection>

    // Fetch-joined so the caller can safely read/serialize `documents` after this transaction closes,
    // without a separate Hibernate.initialize() call (open-in-view is disabled).
    @Query("SELECT c FROM collections c LEFT JOIN FETCH c.documents WHERE c.studyId = :studyId AND c.id = :id")
    fun findCollectionByStudyIdAndId(
        @Param("studyId") studyId: String,
        @Param("id") id: Int,
    ): Optional<Collection>

    fun findByStudyDeploymentIdAndName(
        studyDeploymentId: String,
        name: String,
    ): Optional<Collection>

    // Fetch-joined, see findCollectionByStudyIdAndId.
    @Query("SELECT c FROM collections c LEFT JOIN FETCH c.documents WHERE c.studyId = :studyId AND c.name = :name")
    fun findCollectionByStudyIdAndName(
        @Param("studyId") studyId: String,
        @Param("name") name: String,
    ): Optional<Collection>

    fun findCollectionByName(name: String): Optional<Collection>

    // Deliberately NOT fetch-joined: this backs CollectionService.getAllMetadataOnly, whose only
    // caller reads id/studyDeploymentId and never touches `documents`. See that method's doc comment
    // before changing this.
    fun findAllByStudyId(studyId: String): List<Collection>

    @Query(
        nativeQuery = true,
        value = "SELECT id FROM collections WHERE study_id = :studyId",
    )
    fun getCollectionIdsByStudyId(
        @Param("studyId") studyId: String,
    ): List<Int>

    // Fetch-joined, see findCollectionByStudyIdAndId. DISTINCT avoids duplicate Collection rows from
    // the join multiplying one row per document.
    @Query(
        "SELECT DISTINCT c FROM collections c LEFT JOIN FETCH c.documents " +
            "WHERE c.studyId = :studyId AND c.studyDeploymentId = :deploymentId",
    )
    fun findAllByStudyIdAndDeploymentId(
        @Param("studyId") studyId: String,
        @Param("deploymentId") deploymentId: String,
    ): List<Collection>

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM collections WHERE study_deployment_id IN (:deploymentIds)",
    )
    fun deleteAllByDeploymentIds(
        @Param(value = "deploymentIds") deploymentIds: kotlin.collections.Collection<String>,
    )
}
