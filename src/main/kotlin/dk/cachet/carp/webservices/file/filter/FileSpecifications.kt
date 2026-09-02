package dk.cachet.carp.webservices.file.filter

import dk.cachet.carp.webservices.file.domain.File
import org.springframework.data.jpa.domain.Specification

object FileSpecifications {
    /**
     * The [belongsToStudyId] function validates whether the file is associated with the given [studyId].
     *
     * @param studyId The [studyId] the file is associated with.
     * @return The validated criteria request.
     */
    fun belongsToStudyId(studyId: String): Specification<File> {
        return Specification<File> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<String>("studyId"),
                studyId,
            )
        }
    }

    /**
     * The [belongsToUserAccountId] function validates whether the file was created by the given [accountId].
     *
     * @param accountId The [accountId] the file was created by.
     * @return The validated criteria request.
     */
    fun belongsToUserAccountId(accountId: String): Specification<File> {
        return Specification<File> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<String>("createdBy"),
                accountId,
            )
        }
    }
}
