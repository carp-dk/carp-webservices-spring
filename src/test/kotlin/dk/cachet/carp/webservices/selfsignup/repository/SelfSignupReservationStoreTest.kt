package dk.cachet.carp.webservices.selfsignup.repository

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import kotlin.test.assertEquals

/**
 * Unit coverage for the statement ORDER inside [SelfSignupReservationStore.finalize] - a property real
 * concurrent-transaction timing can't reliably exercise (the race it prevents is a few-statement-wide
 * window), so it's verified directly instead: [SelfSignupReservationStorePostgresTest] covers finalize's
 * actual data effects against a real database.
 */
class SelfSignupReservationStoreTest {
    private val jdbcTemplate: JdbcTemplate = mockk()
    private val transactionManager: PlatformTransactionManager = mockk()
    private val store = SelfSignupReservationStore(jdbcTemplate, transactionManager)
    private val calls = mutableListOf<String>()

    @Test
    fun `finalize locks study_self_signup before touching the reservation at all`() {
        every {
            jdbcTemplate.query(any<String>(), StudySelfSignupStore.rowMapper, "study-1")
        } answers {
            calls += "lock"
            emptyList()
        }
        every {
            jdbcTemplate.update(match<String> { it.startsWith("DELETE") }, *anyVararg())
        } answers {
            calls += "delete"
            1
        }
        every {
            jdbcTemplate.update(match<String> { it.startsWith("UPDATE") }, *anyVararg())
        } answers {
            calls += "update"
            1
        }

        val result = store.finalize("reservation-1", "study-1")

        assertEquals(true, result)
        // The whole point of the fix: the row lock must be taken BEFORE the reservation is even looked at,
        // matching tryClaim's own lock-first order - otherwise a concurrent tryClaim landing between
        // "delete" and "update" could oversell capacity (see the migration/finalize's doc for the race).
        assertEquals(listOf("lock", "delete", "update"), calls)
    }

    @Test
    fun `finalize still locks first even when the reservation has already expired`() {
        every {
            jdbcTemplate.query(any<String>(), StudySelfSignupStore.rowMapper, "study-1")
        } answers {
            calls += "lock"
            emptyList()
        }
        every {
            jdbcTemplate.update(match<String> { it.startsWith("DELETE") }, *anyVararg())
        } answers {
            calls += "delete"
            0
        }

        val result = store.finalize("reservation-1", "study-1")

        assertEquals(false, result)
        // No "update" here: an expired reservation must abort before the confirmed-count bump, same as
        // before the fix - only the lock's position relative to "delete" changed.
        assertEquals(listOf("lock", "delete"), calls)
    }
}
