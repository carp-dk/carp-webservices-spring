package dk.cachet.carp.webservices.protocol.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceRequest
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.protocol.domain.Protocol
import dk.cachet.carp.webservices.protocol.dto.ProtocolOverview
import dk.cachet.carp.webservices.protocol.repository.ProtocolRepository
import dk.cachet.carp.webservices.protocol.service.ProtocolService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinInstant
import org.springframework.stereotype.Service

@Service
class ProtocolServiceWrapper(
    private val accountService: AccountService,
    private val protocolRepository: ProtocolRepository,
    private val applicationDataService: ApplicationDataService,
    services: CoreServiceContainer,
) : ProtocolService {
    final override val core = services.protocolService

    override suspend fun invoke(request: ProtocolServiceRequest<*>): Any? =
        when (request) {
            is ProtocolServiceRequest.Add ->
                core.invoke(
                    request.copy(
                        protocol =
                            request.protocol.copy(
                                applicationData =
                                    applicationDataService.mergeApplicationData(
                                        request.protocol.applicationData,
                                        mapOf("protocolVersionTag" to request.versionTag),
                                    ),
                            ),
                    ),
                )
            is ProtocolServiceRequest.AddVersion ->
                core.invoke(
                    request.copy(
                        protocol =
                            request.protocol.copy(
                                applicationData =
                                    applicationDataService.mergeApplicationData(
                                        request.protocol.applicationData,
                                        mapOf("protocolVersionTag" to request.versionTag),
                                    ),
                            ),
                    ),
                )
            is ProtocolServiceRequest.UpdateParticipantDataConfiguration,
            is ProtocolServiceRequest.GetBy,
            is ProtocolServiceRequest.GetAllForOwner,
            is ProtocolServiceRequest.GetVersionHistoryFor,
            -> core.invoke(request)
        }

    override suspend fun getSingleProtocolOverview(protocolId: String): ProtocolOverview? =
        withContext(Dispatchers.IO) {
            val versions = protocolRepository.findAllByIdSortByCreatedAt(protocolId)
            if (versions.isEmpty()) return@withContext null

            createProtocolOverview(versions)
        }

    override suspend fun getProtocolsOverview(accountId: UUID): List<ProtocolOverview> =
        withContext(Dispatchers.IO) {
            val account =
                accountService.findByUUID(accountId)
                    ?: throw IllegalArgumentException("Account with id $accountId is not found.")

            protocolRepository.findAllByOwnerId(account.id!!)
                .filter { it.snapshot != null }
                .groupBy { it.snapshot?.get("id").toString() }
                .map { (_, versions) ->
                    val sorted = versions.sortedBy { it.createdAt }
                    createProtocolOverview(sorted, account)
                }
        }

    /**
     * Get the [ProtocolOverview] from a sorted list of all the versions of a protocol.
     *
     * @param versions A list of all the versions of a protocol sorted by creation date.
     * @param account The account to use in the protocol overview. Will be looked up if not provided.
     */
    private suspend fun createProtocolOverview(
        versions: List<Protocol>,
        account: Account? = null,
    ): ProtocolOverview {
        val snapshot = WS_JSON.decodeFromString<StudyProtocolSnapshot>(versions.last().snapshot!!.toString())
        val owner = account ?: accountService.findByUUID(snapshot.ownerId)

        return ProtocolOverview(
            owner?.fullName,
            versions.first().createdAt?.toKotlinInstant(),
            versions.last().createdAt?.toKotlinInstant(),
            versions.last().versionTag,
            snapshot,
        )
    }
}
