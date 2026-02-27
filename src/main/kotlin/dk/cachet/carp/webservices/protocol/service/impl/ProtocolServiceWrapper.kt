package dk.cachet.carp.webservices.protocol.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.webservices.account.service.AccountService
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
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service

@Service
class ProtocolServiceWrapper(
    private val accountService: AccountService,
    private val protocolRepository: ProtocolRepository,
    services: CoreServiceContainer,
) : ProtocolService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val FALLBACK_SNAPSHOT_MISMATCH_SUFFIX = "_snapshot_mismatch"
    }

    final override val core = services.protocolService

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

    override suspend fun resolveVersionTag(snapshot: StudyProtocolSnapshot): String =
        withContext(Dispatchers.IO) {
            val protocolId = snapshot.id.stringRepresentation
            val versions = protocolRepository.findByParams(protocolId, null)
            check(versions.isNotEmpty()) { "Protocol with id \"$protocolId\" is not found." }

            val matchingVersion =
                versions.firstOrNull { version ->
                    val storedSnapshotNode = version.snapshot ?: return@firstOrNull false
                    val storedSnapshot = WS_JSON.decodeFromString<StudyProtocolSnapshot>(storedSnapshotNode.toString())
                    storedSnapshot == snapshot
                }

            if (matchingVersion != null) return@withContext matchingVersion.versionTag

            val fallbackTag = versions.first().versionTag + FALLBACK_SNAPSHOT_MISMATCH_SUFFIX
            LOGGER.warn(
                "No matching protocol snapshot found for protocolId={}, falling back to latest version tag={}.",
                protocolId,
                fallbackTag,
            )
            fallbackTag
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
