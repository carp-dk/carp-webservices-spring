package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.webservices.admin.domain.OwnedStudyCounts
import dk.cachet.carp.webservices.admin.dto.AdminResearcherDto
import dk.cachet.carp.webservices.admin.repository.AdminStudyStore
import dk.cachet.carp.webservices.admin.service.AdminResearcherService
import dk.cachet.carp.webservices.security.authentication.domain.AccountSummary
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

@Service
class AdminResearcherServiceImpl(
    private val issuerFacade: IssuerFacade,
    private val adminStudyStore: AdminStudyStore,
) : AdminResearcherService {
    companion object {
        private val NO_STUDIES = OwnedStudyCounts(studyCount = 0, liveStudyCount = 0)
    }

    override suspend fun getResearchers(): List<AdminResearcherDto> =
        coroutineScope {
            val studyCounts = async(Dispatchers.IO) { adminStudyStore.countStudiesByOwner() }
            val researchers = issuerFacade.getAccountsWithRole(Role.RESEARCHER)
            val countsByOwner = studyCounts.await()

            researchers
                // Newest account first; accounts without a created time (null sorts lowest) go last.
                .sortedWith(compareByDescending<AccountSummary> { it.createdAt }.thenBy { it.id })
                .map { it.toDto(countsByOwner[it.id] ?: NO_STUDIES) }
        }

    private fun AccountSummary.toDto(counts: OwnedStudyCounts) =
        AdminResearcherDto(
            id = id,
            username = username,
            email = email,
            fullName = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null },
            enabled = enabled,
            emailVerified = emailVerified,
            createdAt = createdAt,
            ownedStudyCount = counts.studyCount,
            liveStudyCount = counts.liveStudyCount,
        )
}
