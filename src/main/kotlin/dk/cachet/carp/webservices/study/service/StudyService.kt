package dk.cachet.carp.webservices.study.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.infrastructure.StudyServiceDecorator
import dk.cachet.carp.studies.infrastructure.StudyServiceRequest
import dk.cachet.carp.webservices.study.domain.StudyOverview

interface StudyService {
    val core: StudyServiceDecorator

    suspend fun invoke(request: StudyServiceRequest<*>): Any?

    suspend fun getStudiesOverview(accountId: UUID): List<StudyOverview>
}
