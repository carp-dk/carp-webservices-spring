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
                    CASE
                        WHEN btrim((latest.snapshot->'measurements'->(jsonb_array_length(latest.snapshot->'measurements') - 1))->'data'->>'latitude') ~
                             '^[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][-+]?\\d+)?$'
                        THEN ((latest.snapshot->'measurements'->(jsonb_array_length(latest.snapshot->'measurements') - 1))->'data'->>'latitude')::double precision
                        ELSE NULL
                    END AS latitude,
                    CASE
                        WHEN btrim((latest.snapshot->'measurements'->(jsonb_array_length(latest.snapshot->'measurements') - 1))->'data'->>'longitude') ~
                             '^[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][-+]?\\d+)?$'
                        THEN ((latest.snapshot->'measurements'->(jsonb_array_length(latest.snapshot->'measurements') - 1))->'data'->>'longitude')::double precision
                        ELSE NULL
                    END AS longitude
                FROM (
                    SELECT DISTINCT ON (data_stream_id) id, data_stream_id, snapshot, created_at
                    FROM data_stream_sequence
                    WHERE data_stream_id IN (:dataStreamIds)
                        AND created_at >= :from
                        AND jsonb_typeof(snapshot->'measurements') = 'array'
                        AND jsonb_array_length(snapshot->'measurements') > 0
                    ORDER BY data_stream_id, created_at DESC, id DESC
                ) latest
                ORDER BY latest.created_at DESC, latest.id DESC
                LIMIT :limit
            """,
    )
    fun getLatestLocationCoordinatesByDataStreamIds(
        @Param("dataStreamIds") dataStreamIds: Collection<Int>,
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
