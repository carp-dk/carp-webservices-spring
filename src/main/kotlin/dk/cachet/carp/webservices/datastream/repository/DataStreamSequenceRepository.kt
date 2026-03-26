package dk.cachet.carp.webservices.datastream.repository

import dk.cachet.carp.webservices.datastream.domain.DataStreamSequence
import dk.cachet.carp.webservices.datastream.dto.DateQuantityPairDb
import dk.cachet.carp.webservices.datastream.dto.DateTaskQuantityTripleDb
import dk.cachet.carp.webservices.datastream.dto.LocationCoordinatesDb
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface DataStreamSequenceRepository : JpaRepository<DataStreamSequence, Int> {
    @Query(
        nativeQuery = true,
        value =
            "SELECT * FROM data_stream_sequence " +
                "WHERE data_stream_id = :dataStreamId " +
                "AND ( (first_sequence_id <= :from AND last_sequence_id >= :from) " +
                "OR (last_sequence_id <= :to AND last_sequence_id >= :from) " +
                "OR (first_sequence_id <= :to AND first_sequence_id >= :from) )",
    )
    fun findAllBySequenceIdRange(
        @Param("dataStreamId") dataStreamId: Int,
        @Param("from") from: Long,
        @Param("to") to: Long,
    ): List<DataStreamSequence>

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM data_stream_sequence WHERE data_stream_id IN (:ids)",
    )
    fun deleteAllByDataStreamIds(
        @Param(value = "ids") ids: Collection<Int>,
    )

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM data_stream_sequence WHERE data_stream_id IN (:dataStreamIds)",
    )
    fun findAllByDataStreamIds(
        @Param("dataStreamIds") dataStreamIds: Collection<Int>,
    ): List<DataStreamSequence>

    @Query(
        """
    SELECT MAX(dss.updatedAt) 
    FROM data_stream_sequence dss
    WHERE dss.dataStreamId IN :dataStreamIds
    """,
    )
    fun findMaxUpdatedAtByDataStreamIds(
        @Param("dataStreamIds") dataStreamIds: List<Int>,
    ): Instant?

    @Query(
        """
    SELECT dsq.id
    FROM data_stream_sequence dsq
    WHERE dsq.dataStreamId IN :dataStreamIds
    """,
    )
    fun findSequenceIdsByStreamId(
        @Param("dataStreamIds") dataStreamIds: List<Int>,
    ): List<Int>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT
                    DATE(created_at AT TIME ZONE 'UTC') AS date,
                    COUNT(*) AS quantity
                FROM data_stream_sequence
                WHERE created_at >= :from
                GROUP BY DATE(created_at AT TIME ZONE 'UTC')
                ORDER BY DATE(created_at AT TIME ZONE 'UTC')
            """,
    )
    fun getDailyUploadCountsSince(
        from: Instant,
    ): List<DateQuantityPairDb>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT
                    (latest.measurement_data->>'latitude')::double precision AS latitude,
                    (latest.measurement_data->>'longitude')::double precision AS longitude
                FROM (
                    SELECT DISTINCT ON (dss.data_stream_id)
                        dss.id,
                        dss.data_stream_id,
                        dss.created_at,
                        measurement.value->'data' AS measurement_data
                    FROM data_stream_sequence dss
                    JOIN data_stream_ids dsi ON dsi.id = dss.data_stream_id
                    CROSS JOIN LATERAL (
                        SELECT value
                        FROM jsonb_array_elements(dss.snapshot->'measurements') WITH ORDINALITY AS measurement_item(value, ordinality)
                        ORDER BY ordinality DESC
                        LIMIT 1
                    ) measurement
                    WHERE dsi.name = :name
                        AND dss.created_at >= :from
                        AND jsonb_typeof(dss.snapshot->'measurements') = 'array'
                        AND jsonb_array_length(dss.snapshot->'measurements') > 0
                    ORDER BY dss.data_stream_id, dss.created_at DESC, dss.id DESC
                ) latest
                ORDER BY latest.created_at DESC, latest.id DESC
                LIMIT :limit
            """,
    )
    fun getLatestLocationCoordinatesByDataStreamName(
        @Param("name") name: String,
        @Param("from") from: Instant,
        @Param("limit") limit: Int,
    ): List<LocationCoordinatesDb>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT 
                    (measurement->'data'->>'completedAt')::timestamp AS date,
                    measurement->'data'->>'taskName' AS task_name,
                    COUNT(*) AS quantity
                FROM public.data_stream_sequence ds,
                     LATERAL jsonb_array_elements(ds.snapshot->'measurements') AS measurement
                WHERE ds.data_stream_id IN (:dataStreamIds)
                    AND measurement->'data'->>'__type' = 'dk.cachet.carp.completedapptask'
                    AND measurement->'data'->>'taskType' = :taskType
                    AND (measurement->'data'->>'completedAt')::timestamp > :from
                    AND (measurement->'data'->>'completedAt')::timestamp < :to
                GROUP BY date, task_name
                ORDER BY date DESC 
            """,
    )
    fun getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
        dataStreamIds: List<Int>,
        from: Instant,
        to: Instant,
        taskType: String,
    ): List<DateTaskQuantityTripleDb>

    @Query(
        nativeQuery = true,
        value =
            """
                SELECT 
                    (measurement->'data'->>'completedAt')::timestamp AS date,
                    measurement->'data'->>'taskName' AS task_name,
                    COUNT(*) AS quantity
                FROM public.data_stream_sequence ds,
                     LATERAL jsonb_array_elements(ds.snapshot->'measurements') AS measurement
                WHERE ds.data_stream_id IN (:dataStreamIds)
                    AND measurement->'data'->>'__type' = :completedAppTaskType
                    AND (measurement->'data'->>'completedAt')::timestamp > :from
                    AND (measurement->'data'->>'completedAt')::timestamp < :to
                GROUP BY date, task_name
                ORDER BY date DESC 
            """,
    )
    fun getDayKeyQuantityListByDataStreamIdsAndOtherParametersV2(
        dataStreamIds: List<Int>,
        from: Instant,
        to: Instant,
        completedAppTaskType: String,
    ): List<DateTaskQuantityTripleDb>
}
