package dk.cachet.carp.webservices.datastream.repository

import dk.cachet.carp.webservices.datastream.domain.DataStreamId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
@Suppress("TooManyFunctions")
interface DataStreamIdRepository : JpaRepository<DataStreamId, Int> {
    @Query(
        nativeQuery = true,
        value =
            """
                SELECT id
                FROM data_stream_ids
                WHERE name = :name
            """,
    )
    fun getAllIdsByName(
        @Param("name") name: String,
    ): List<Int>

    fun findByStudyDeploymentIdAndDeviceRoleNameAndNameAndNameSpace(
        studyDeploymentId: String,
        deviceRoleName: String,
        name: String,
        nameSpace: String,
    ): Optional<DataStreamId>

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM data_stream_ids WHERE id IN (:ids)",
    )
    fun deleteAllByDataStreamIds(
        @Param(value = "ids") ids: Collection<Int>,
    )

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM data_stream_ids WHERE study_deployment_id IN (:deploymentIds)",
    )
    fun deleteAllByDeploymentIds(
        @Param(value = "deploymentIds") deploymentIds: Collection<String>,
    )

    @Query(
        nativeQuery = true,
        value = "SELECT id FROM data_stream_ids WHERE study_deployment_id IN (:deploymentIds)",
    )
    fun getAllByDeploymentIds(
        @Param("deploymentIds") ids: Collection<String>,
    ): List<Int>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT id
                FROM data_stream_ids
                WHERE study_deployment_id IN (:deploymentIds)
                    AND name_space = :nameSpace
                    AND name = :name
            """,
    )
    fun getAllIdsByDeploymentIdsAndNameSpaceAndName(
        @Param("deploymentIds") deploymentIds: Collection<String>,
        @Param("nameSpace") nameSpace: String,
        @Param("name") name: String,
    ): List<Int>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT id
                FROM data_stream_ids
                WHERE study_deployment_id = :deploymentId
                    AND name_space = :nameSpace
                    AND name = :name
            """,
    )
    fun getAllIdsByDeploymentIdAndNameSpaceAndName(
        @Param("deploymentId") deploymentId: String,
        @Param("nameSpace") nameSpace: String,
        @Param("name") name: String,
    ): List<Int>

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM data_stream_ids WHERE study_deployment_id = :deploymentId ",
    )
    fun getAllByDeploymentId(
        @Param("deploymentId") id: String,
    ): List<DataStreamId>

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM data_stream_ids WHERE id = :ids ",
    )
    fun findByDataStreamId(
        @Param("ids") ids: Int,
    ): DataStreamId?

    fun getAllByStudyDeploymentIdAndDeviceRoleNameIn(
        studyDeploymentId: String,
        deviceRoleNames: MutableCollection<String>,
    ): MutableList<DataStreamId>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT id
                FROM data_stream_ids
                WHERE study_deployment_id = :deploymentId
                    AND device_role_name IN (:deviceRoleNames)
                    AND name_space = :nameSpace
                    AND name = :name
            """,
    )
    fun getAllIdsByDeploymentIdAndDeviceRoleNameInAndNameSpaceAndName(
        @Param("deploymentId") deploymentId: String,
        @Param("deviceRoleNames") deviceRoleNames: Collection<String>,
        @Param("nameSpace") nameSpace: String,
        @Param("name") name: String,
    ): List<Int>
}
