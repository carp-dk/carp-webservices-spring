package dk.cachet.carp.webservices.datastream.repository

import dk.cachet.carp.webservices.datastream.domain.DataStreamSequence
import dk.cachet.carp.webservices.datastream.dto.DateTaskQuantityTripleDb
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
}
