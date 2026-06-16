package dk.cachet.carp.webservices.dataPoint.repository

import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.file.service.impl.FileStorageImpl
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Repository
interface DataPointRepository :
    JpaRepository<DataPoint, String>,
    JpaSpecificationExecutor<DataPoint>,
    DataPointRepositoryCustom {
    companion object {
        /**
         * A nested interface, mainly used as a projection class for the [getStatistics] function.
         * TODO: Okay, but why is it here?
         */
        interface Statistics {
            /** The ID of the deployment the data points are connected to. */
            var did: String

            /** Total number of data points on a given day. */
            var total: Int

            /** Contains the dataFormat's name from the [DataPointHeaderDtos] */
            var format: String

            /** Represents a day. */
            var stamp: String
        }
    }

    fun findById(id: Int): Optional<DataPoint>

    fun findByDeploymentId(
        deploymentId: String,
        pageable: Pageable,
    ): Page<DataPoint>

    /** The [findAllByDeploymentIds] interface to retrieve the data point by several [deploymentId]s. */
    @Query(value = "SELECT dp FROM data_points dp WHERE dp.deploymentId IN :deploymentIds")
    fun findAllByDeploymentIds(
        @Param("deploymentIds") deploymentIds: Collection<String>,
    ): List<DataPoint>

    fun findByDeploymentIdAndCreatedBy(
        deploymentId: String,
        createdBy: String,
        pageable: Pageable,
    ): Page<DataPoint>

    fun countByDeploymentId(deploymentId: String): Long

    fun countByDeploymentIdAndCreatedBy(
        deploymentId: String,
        createdBy: String,
    ): Long

    /** The [getStatistics] interface returns statistical information about the given deployments. */
    @Query(
        nativeQuery = true,
        value =
            "select deployment_id as did, " +
                "count(*) as total, " +
                "carp_header->'data_format'->>'name' as format, " +
                "cast(created_at as DATE) as stamp " +
                "from data_points where deployment_id in (:ids) group by deployment_id, stamp, format",
    )
    fun getStatistics(
        @Param("ids") deploymentIds: Collection<String>,
    ): List<Statistics>

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM data_points WHERE deployment_id IN (:deploymentIds)",
    )
    fun deleteAllByDeploymentIds(
        @Param(value = "deploymentIds") deploymentIds: Collection<String>,
    )

    override fun save(
        dataPoint: DataPoint,
        uploadedFile: MultipartFile?,
    ): DataPoint
}

/**
 * The [DataPointRepositoryCustom] interface implements a repository for
 * [DataPoint] to insert data with the given [dataPoint] and [uploadedFile].
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
interface DataPointRepositoryCustom {
    fun save(
        dataPoint: DataPoint,
        uploadedFile: MultipartFile?,
    ): DataPoint
}

/**
 * The [DataPointRepositoryImpl] implements the [DataPointRepositoryCustom] to insert the new data points.
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Repository
class DataPointRepositoryImpl(
    @Lazy private val dataPointRepository: DataPointRepository,
    private val fileStorage: FileStorageImpl,
) : DataPointRepositoryCustom {
    /**
     * The function [save] inserts data with the given [dataPoint] and [uploadedFile].
     * @param dataPoint The [dataPoint] to be inserted.
     * @param uploadedFile The [uploadedFile] to be uploaded.
     * @return A [DataPoint] containing the data point inserted.
     */
    override fun save(
        dataPoint: DataPoint,
        uploadedFile: MultipartFile?,
    ): DataPoint {
        if (uploadedFile !== null) {
            dataPoint.storageName = fileStorage.store(uploadedFile)
        }
        return dataPointRepository.save(dataPoint)
    }
}
