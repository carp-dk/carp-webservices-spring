package dk.cachet.carp.webservices.collection.service

import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.dto.CollectionCreateRequestDto
import dk.cachet.carp.webservices.collection.dto.CollectionUpdateRequestDto

interface CollectionService {
    fun delete(
        studyId: String,
        id: Int,
    )

    fun update(
        studyId: String,
        id: Int,
        updateRequest: CollectionUpdateRequestDto,
    ): Collection

    fun create(
        request: CollectionCreateRequestDto,
        studyId: String,
        deploymentId: String?,
    ): Collection

    fun getCollectionByStudyIdAndId(
        studyId: String,
        id: Int,
    ): Collection

    fun getCollectionByStudyIdAndByName(
        studyId: String,
        name: String,
    ): Collection

    fun getAll(
        studyId: String,
        query: String?,
    ): List<Collection>

    /**
     * Metadata-only lookup: unlike the other `getAll` variants, this does not initialize [Collection.documents],
     * since its only caller (export) filters on id/studyDeploymentId and fetches documents separately. If a new
     * caller needs documents populated, use [getAll] or [getAllByStudyIdAndDeploymentId] instead of adding
     * initialization here.
     */
    fun getAllMetadataOnly(studyId: String): List<Collection>

    fun getAllByStudyIdAndDeploymentId(
        studyId: String,
        deploymentId: String,
    ): List<Collection>
}
