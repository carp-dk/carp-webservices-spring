package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.admin.domain.AdminStudyRow
import dk.cachet.carp.webservices.admin.dto.AdminStudyDto
import dk.cachet.carp.webservices.admin.dto.StudyOwnerDto
import dk.cachet.carp.webservices.admin.repository.AdminStudyStore
import dk.cachet.carp.webservices.admin.service.AdminStudyService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import kotlin.coroutines.cancellation.CancellationException

@Service
class AdminStudyServiceImpl(
    private val adminStudyStore: AdminStudyStore,
    private val issuerFacade: IssuerFacade,
    private val accountService: AccountService,
    private val applicationDataService: ApplicationDataService,
) : AdminStudyService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Concurrent Keycloak lookups for owners that are not in the researcher listing. */
        private const val OWNER_LOOKUP_CONCURRENCY = 8
    }

    /** What the listing shows about a study owner. */
    private data class OwnerContact(val email: String?, val fullName: String?)

    override suspend fun getStudies(live: Boolean?): List<AdminStudyDto> {
        val rows = withContext(Dispatchers.IO) { adminStudyStore.findAll(live) }
        val owners = resolveOwners(rows.mapNotNull { it.ownerId }.toSet())
        return rows.map { it.toDto(owners[it.ownerId]) }
    }

    /**
     * Owners are nearly always researchers, so one paged listing of the researcher role resolves most of them.
     * The rest (e.g. an owner whose researcher role was removed) are looked up one by one; a missing account or
     * a failed lookup leaves that owner's details null rather than failing the listing.
     */
    private suspend fun resolveOwners(ownerIds: Set<String>): Map<String, OwnerContact> {
        if (ownerIds.isEmpty()) return emptyMap()

        val fromListing =
            listResearchers()
                .filter { it.id in ownerIds }
                .associate { it.id to OwnerContact(it.email, fullName(it.firstName, it.lastName)) }

        return fromListing + lookUpIndividually(ownerIds - fromListing.keys)
    }

    private suspend fun listResearchers() =
        try {
            issuerFacade.getAccountsWithRole(Role.RESEARCHER)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            LOGGER.warn("Could not list researchers to resolve study owners; looking each one up instead.", e)
            emptyList()
        }

    private suspend fun lookUpIndividually(ownerIds: Set<String>): Map<String, OwnerContact> =
        coroutineScope {
            val permits = Semaphore(OWNER_LOOKUP_CONCURRENCY)
            ownerIds
                .map { ownerId ->
                    async(Dispatchers.IO) {
                        // findByUUID already returns null for missing accounts and failed lookups.
                        val account =
                            runCatching { UUID(ownerId) }.getOrNull()?.let { uuid ->
                                permits.withPermit { accountService.findByUUID(uuid) }
                            }
                        account?.let { ownerId to OwnerContact(it.email, fullName(it.firstName, it.lastName)) }
                    }
                }.awaitAll()
                .filterNotNull()
                .toMap()
        }

    // Not Account.fullName: that renders missing names as the string "null".
    private fun fullName(
        firstName: String?,
        lastName: String?,
    ): String? = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }

    private fun AdminStudyRow.toDto(owner: OwnerContact?) =
        AdminStudyDto(
            studyId = studyId,
            name = name,
            description = description,
            isLive = isLive,
            applicationName = applicationDataService.extractApplicationName(applicationData),
            owner = StudyOwnerDto(id = ownerId, email = owner?.email, fullName = owner?.fullName),
            createdAt = createdAt,
            updatedAt = updatedAt,
            firstDeploymentAt = firstDeploymentAt,
            participantCount = participantCount,
            deploymentCount = deploymentCount,
            fileCount = fileCount,
        )
}
