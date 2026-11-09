package dk.cachet.carp.webservices.admin.service

import dk.cachet.carp.webservices.admin.dto.AdminResearcherDto

interface AdminResearcherService {
    /** Every researcher account on this instance with the number of studies it owns, newest account first. */
    suspend fun getResearchers(): List<AdminResearcherDto>
}
