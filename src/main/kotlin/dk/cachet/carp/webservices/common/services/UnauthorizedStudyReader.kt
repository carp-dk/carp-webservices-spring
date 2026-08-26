package dk.cachet.carp.webservices.common.services

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.StudyDetails
import dk.cachet.carp.studies.application.StudyService
import dk.cachet.carp.studies.application.StudyStatus
import dk.cachet.carp.webservices.study.authorization.StudyServiceAuthorizer

/**
 * A narrow, read-only view of [StudyService] for trusted callers with no authenticated principal on the
 * request (e.g. self-signup's public endpoint), which therefore cannot go through the normal decorated,
 * authorized service. Exposes only the two read methods those callers actually need, instead of the full
 * mutating [StudyService] surface (`createStudy`, `goLive`, `setProtocol`, `remove`, ...) - narrowing this
 * to an interface means a future caller reaching for study data from another unauthenticated context gets
 * a compile error if it tries anything beyond a read, rather than silently gaining access to lifecycle
 * operations that were never meant to be reachable without [StudyServiceAuthorizer].
 */
interface UnauthorizedStudyReader {
    suspend fun getStudyDetails(studyId: UUID): StudyDetails

    suspend fun getStudyStatus(studyId: UUID): StudyStatus
}
