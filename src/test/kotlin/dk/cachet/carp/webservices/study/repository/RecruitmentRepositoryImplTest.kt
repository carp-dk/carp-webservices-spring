package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.text.Regex

class RecruitmentRepositoryImplTest {
    @Test
    fun `query participant accounts returns ordered rows for deployed sort`() {
        val entityManager = mockk<EntityManager>()
        val query = mockk<Query>(relaxed = true)
        var capturedSql = ""

        every { entityManager.createNativeQuery(any<String>()) } answers {
            capturedSql = firstArg()
            query
        }
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns listOf(arrayOf("""{"id":"p1"}""", true, "deployment-1"))

        val sut = RecruitmentRepositoryImpl(entityManager)

        val result =
            sut.queryParticipantAccounts(
                studyId = "study-id",
                offset = 0,
                limit = 10,
                search = null,
                isDeployed = null,
                sortDirection = SortDirection.Desc,
                sortBy = ParticipantOrderBy.IsDeployed,
            )

        val normalizedSql = capturedSql.replace(Regex("\\s+"), " ").trim()

        assertEquals(1, result.size)
        assertEquals("""{"id":"p1"}""", result[0].participantJson)
        assertEquals(true, result[0].isDeployed)
        assertEquals("deployment-1", result[0].deploymentId)
        assertContains(
            normalizedSql,
            """
            SELECT elem AS participant,
            deployed_participant.participant_id IS NOT NULL AS is_deployed,
            deployed_participant.deployment_id
            """.replace(Regex("\\s+"), " ").trim(),
        )
        assertContains(
            normalizedSql,
            "jsonb_array_elements(snapshot->'participants') WITH ORDINALITY arr(elem, idx)",
        )
        assertContains(
            normalizedSql,
            """
            ORDER BY
            CASE WHEN deployed_participant.participant_id IS NULL THEN 0 ELSE 1 END DESC,
            COALESCE(
                elem->'accountIdentity'->>'username',
                elem->'accountIdentity'->>'emailAddress'
            ) DESC,
            idx DESC
            LIMIT :limit OFFSET :offset
            """.replace(Regex("\\s+"), " ").trim(),
        )
        verify { entityManager.createNativeQuery(any<String>()) }
        verify { query.setParameter("studyId", "study-id") }
        verify { query.setParameter("limit", 10) }
        verify { query.setParameter("offset", 0) }
    }

    @Test
    fun `query participant accounts omits pagination when page is not requested`() {
        val entityManager = mockk<EntityManager>()
        val query = mockk<Query>(relaxed = true)
        var capturedSql = ""

        every { entityManager.createNativeQuery(any<String>()) } answers {
            capturedSql = firstArg()
            query
        }
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns listOf(arrayOf("""{"id":"p1"}""", false, null))

        val sut = RecruitmentRepositoryImpl(entityManager)

        val result =
            sut.queryParticipantAccounts(
                studyId = "study-id",
                offset = null,
                limit = null,
                search = null,
                isDeployed = null,
                sortDirection = null,
                sortBy = null,
            )

        val normalizedSql = capturedSql.replace(Regex("\\s+"), " ").trim()

        assertEquals(1, result.size)
        assertEquals(null, result[0].deploymentId)
        assertFalse(normalizedSql.contains("LIMIT :limit OFFSET :offset"))
        verify { query.setParameter("studyId", "study-id") }
        verify(exactly = 0) { query.setParameter("limit", any()) }
        verify(exactly = 0) { query.setParameter("offset", any()) }
    }
}
