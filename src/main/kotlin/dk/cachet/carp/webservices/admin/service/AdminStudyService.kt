package dk.cachet.carp.webservices.admin.service

import dk.cachet.carp.webservices.admin.dto.AdminStudyDto

interface AdminStudyService {
    /** Every study on this instance with owner and recruitment figures; only live/non-live ones when [live] is set. */
    suspend fun getStudies(live: Boolean?): List<AdminStudyDto>
}
