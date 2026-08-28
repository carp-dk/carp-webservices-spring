package dk.cachet.carp.webservices.dataPoint.filter

import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import org.springframework.data.jpa.domain.Specification

object DataPointSpecifications {
    /**
     * The [belongsToDeploymentId] function validates whether the data point is associated with the given
     * [deploymentId].
     *
     * @param deploymentId The [deploymentId] the data point is associated with.
     * @return The validated criteria request.
     */
    fun belongsToDeploymentId(deploymentId: String): Specification<DataPoint> {
        return Specification<DataPoint> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<String>("deploymentId"),
                deploymentId,
            )
        }
    }

    /**
     * The [belongsToUserAccountId] function validates whether the data point was created by the given [accountId].
     *
     * @param accountId The [accountId] the data point was created by.
     * @return The validated criteria request.
     */
    fun belongsToUserAccountId(accountId: String): Specification<DataPoint> {
        return Specification<DataPoint> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<String>("createdBy"),
                accountId,
            )
        }
    }
}
