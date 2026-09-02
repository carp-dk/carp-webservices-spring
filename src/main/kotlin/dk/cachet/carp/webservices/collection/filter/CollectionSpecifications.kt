package dk.cachet.carp.webservices.collection.filter

import dk.cachet.carp.webservices.collection.domain.Collection
import org.springframework.data.jpa.domain.Specification

object CollectionSpecifications {
    /**
     * The [belongsToStudyId] function validates whether the collection is associated with the given [studyId].
     *
     * @param studyId The [studyId] the collection is associated with.
     * @return The validated criteria request.
     */
    fun belongsToStudyId(studyId: String): Specification<Collection> {
        return Specification<Collection> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<String>("studyId"),
                studyId,
            )
        }
    }
}
