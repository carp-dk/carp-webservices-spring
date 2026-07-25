package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit coverage for the Kotlin-side logic of [RecruitmentRepositoryImpl] — row mapping and the
 * pagination-parameter gating. Correctness of the relational SQL itself is validated against a real
 * database with normalized data (see the participant-search equivalence check), not by asserting on
 * SQL text, which is brittle.
 */
class RecruitmentRepositoryImplTest {
    @Test
    fun `query participant accounts maps rows and binds pagination params`() {
        val entityManager = mockk<EntityManager>()
        val query = mockk<Query>(relaxed = true)
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns listOf(arrayOf<Any?>("""{"id":"p1"}""", true, "deployment-1"))

        val result =
            RecruitmentRepositoryImpl(entityManager).queryParticipantAccounts(
                studyId = "study-id",
                offset = 0,
                limit = 10,
                search = null,
                isDeployed = null,
                sortDirection = SortDirection.Desc,
                sortBy = ParticipantOrderBy.IsDeployed,
            )

        assertEquals(1, result.size)
        assertEquals("""{"id":"p1"}""", result[0].participantJson)
        assertEquals(true, result[0].isDeployed)
        assertEquals("deployment-1", result[0].deploymentId)
        verify { query.setParameter("studyId", "study-id") }
        verify { query.setParameter("limit", 10) }
        verify { query.setParameter("offset", 0) }
    }

    @Test
    fun `query participant accounts omits pagination params when page is not requested`() {
        val entityManager = mockk<EntityManager>()
        val query = mockk<Query>(relaxed = true)
        var capturedSql = ""
        every { entityManager.createNativeQuery(any<String>()) } answers {
            capturedSql = firstArg()
            query
        }
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns listOf(arrayOf<Any?>("""{"id":"p1"}""", false, null))

        val result =
            RecruitmentRepositoryImpl(entityManager).queryParticipantAccounts(
                studyId = "study-id",
                offset = null,
                limit = null,
                search = null,
                isDeployed = null,
                sortDirection = null,
                sortBy = null,
            )

        assertEquals(1, result.size)
        assertEquals(null, result[0].deploymentId)
        assertFalse(capturedSql.contains("LIMIT :limit OFFSET :offset"))
        verify(exactly = 0) { query.setParameter("limit", any()) }
        verify(exactly = 0) { query.setParameter("offset", any()) }
    }
}
