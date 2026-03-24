package dk.cachet.carp.webservices.statistics.service.impl

import dk.cachet.carp.webservices.statistics.dto.StatisticsOverviewDto
import dk.cachet.carp.webservices.statistics.service.StatisticsService
import dk.cachet.carp.webservices.study.repository.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class StatisticsServiceImpl(
    private val studyRepository: StudyRepository,
) : StatisticsService {
    override suspend fun getOverview(): StatisticsOverviewDto =
        withContext(Dispatchers.IO) {
            StatisticsOverviewDto(
                totalLiveStudies = studyRepository.countLiveStudies(),
                totalParticipants = 0,
                totalResearchers = 0,
                dailyDatastreamUploads = emptyMap(),
                operationsByApplications = emptyMap(),
                locationwiseDataUploads = emptyMap(),
            )
        }
}
