package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.admin.domain.AdminStudyRow
import dk.cachet.carp.webservices.admin.dto.AdminStudyDto
import dk.cachet.carp.webservices.admin.dto.StudyOwnerDto
import dk.cachet.carp.webservices.admin.repository.AdminStudyStore
import dk.cachet.carp.webservices.admin.service.AdminStudyService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class AdminStudyServiceImpl(
    private val adminStudyStore: AdminStudyStore,
    private val accountService: AccountService,
    private val applicationDataService: ApplicationDataService,
) : AdminStudyService {
    companion object {
        /** Concurrent Keycloak lookups while resolving owners. */
        private const val OWNER_LOOKUP_CONCURRENCY = 8
    }

    override suspend fun getStudies(live: Boolean?): List<AdminStudyDto> {
        val rows = withContext(Dispatchers.IO) { adminStudyStore.findAll(live) }
        val owners = resolveOwners(rows.mapNotNull { it.ownerId }.toSet())
        return rows.map { it.toDto(owners[it.ownerId]) }
    }

    /**
     * Looks up each distinct owner once - O(researchers), not O(studies). [AccountService.findByUUID] already
     * returns null for missing accounts and failed lookups, so one bad owner doesn't fail the listing.
     */
    private suspend fun resolveOwners(ownerIds: Set<String>): Map<String, Account?> =
        coroutineScope {
            val permits = Semaphore(OWNER_LOOKUP_CONCURRENCY)
            ownerIds
                .map { ownerId ->
                    async(Dispatchers.IO) {
                        val account =
                            runCatching { UUID(ownerId) }.getOrNull()?.let { uuid ->
                                permits.withPermit { accountService.findByUUID(uuid) }
                            }
                        ownerId to account
                    }
                }.awaitAll()
                .toMap()
        }

    // Not Account.fullName: that renders missing names as the string "null".
    private fun Account.displayName(): String? = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }

    private fun AdminStudyRow.toDto(owner: Account?) =
        AdminStudyDto(
            studyId = studyId,
            name = name,
            description = description,
            isLive = isLive,
            applicationName = applicationDataService.extractApplicationName(applicationData),
            owner =
                StudyOwnerDto(
                    id = ownerId,
                    email = owner?.email,
                    fullName = owner?.displayName(),
                ),
            createdAt = createdAt,
            updatedAt = updatedAt,
            firstDeploymentAt = firstDeploymentAt,
            participantCount = participantCount,
            deploymentCount = deploymentCount,
            fileCount = fileCount,
        )
}
