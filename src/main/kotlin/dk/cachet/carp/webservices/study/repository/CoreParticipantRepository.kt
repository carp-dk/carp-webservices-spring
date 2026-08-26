package dk.cachet.carp.webservices.study.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.domain.users.ParticipantRepository
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.study.domain.Recruitment
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizationStore
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import dk.cachet.carp.studies.domain.users.Recruitment as CoreRecruitment

@Service
class CoreParticipantRepository(
    private val recruitmentRepository: RecruitmentRepository,
    private val normalizationStore: RecruitmentNormalizationStore,
    transactionManager: PlatformTransactionManager,
) : ParticipantRepository {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    // Programmatic, not @Transactional: these suspend functions run their bodies inside
    // withContext(Dispatchers.IO), and with the classic (non-reactive) PlatformTransactionManager this app
    // uses, Spring's @Transactional AOP proxy commits as soon as that dispatch suspends - i.e. BEFORE the
    // dispatched body (the actual DB calls) ever runs - so it cannot make a lock-then-write sequence
    // atomic. TransactionTemplate.execute is a plain synchronous call with no coroutine suspension point,
    // so the transaction/connection it opens stays bound to the IO-dispatcher thread for the whole lambda,
    // which is what addRecruitment/updateRecruitment need to hold their row lock across multiple statements.
    private val transactionTemplate = TransactionTemplate(transactionManager)

    // REPEATABLE READ, not just a transaction boundary: getRecruitment/getRecruitmentWithParticipantGroup
    // each do two separate reads (the envelope via findRecruitmentByStudyId, then the normalized
    // participant/group rows via reconstructFromTables) that need to reflect the SAME point in time. At the
    // default READ COMMITTED, each read takes its own fresh snapshot, so a concurrent append()/
    // updateRecruitment() committing in between could make the second read see a state the first read's
    // version doesn't match - a torn read. REPEATABLE READ fixes the whole transaction's snapshot as of its
    // first query, so both reads here are guaranteed consistent with each other.
    private val readTransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
            isReadOnly = true
        }

    override suspend fun addRecruitment(recruitment: CoreRecruitment) =
        withContext(Dispatchers.IO) {
            val studyId = recruitment.studyId.stringRepresentation
            transactionTemplate.executeWithoutResult {
                val existingRecruitment = recruitmentRepository.findRecruitmentByStudyId(studyId)

                check(existingRecruitment == null) { "A recruitment already exists for the study with id $studyId." }

                val normalized = RecruitmentNormalizer.decompose(recruitment.getSnapshot())
                val newRecruitment = Recruitment().apply { this.snapshot = normalized.envelopeSnapshot }
                val saved = recruitmentRepository.save(newRecruitment)
                normalizationStore.replace(saved.id, normalized)
                LOGGER.info("New recruitment with id ${saved.id} is saved for study with id $studyId.")
            }
        }

    override suspend fun getRecruitment(studyId: UUID): CoreRecruitment? =
        withContext(Dispatchers.IO) {
            readTransactionTemplate.execute {
                val existingRecruitment = recruitmentRepository.findRecruitmentByStudyId(studyId.stringRepresentation)

                if (existingRecruitment == null) {
                    LOGGER.info("Recruitment for studyId $studyId is not found.")
                    return@execute null
                }

                reconstructFromTables(existingRecruitment)
            }
        }

    override suspend fun getRecruitmentWithParticipantGroup(groupId: UUID): CoreRecruitment? =
        withContext(Dispatchers.IO) {
            readTransactionTemplate.execute {
                recruitmentRepository
                    .findRecruitmentByNormalizedGroupId(groupId.stringRepresentation)
                    ?.let(::reconstructFromTables)
            }
        }

    /**
     * Resolves the study a deployment belongs to. Backed by an indexed lookup that does not load the
     * recruitment snapshot, so it is cheap enough to call on the authorization hot path.
     */
    fun getStudyIdByDeploymentId(deploymentId: UUID): UUID? {
        val studyId = recruitmentRepository.findStudyIdByNormalizedGroupId(deploymentId.stringRepresentation)
        return studyId?.let { UUID(it) }
    }

    override suspend fun removeStudy(studyId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            getRecruitment(studyId) ?: return@withContext false
            recruitmentRepository.deleteByStudyId(studyId.stringRepresentation)
            LOGGER.info("Recruitment with studyId ${studyId.stringRepresentation} is deleted.")
            true
        }

    override suspend fun updateRecruitment(recruitment: CoreRecruitment) =
        withContext(Dispatchers.IO) {
            val studyId = recruitment.studyId.stringRepresentation
            transactionTemplate.executeWithoutResult {
                val recruitmentFound =
                    recruitmentRepository.findRecruitmentByStudyId(studyId)
                        ?: throw ResourceNotFoundException("Recruitment with studyId $studyId is not found.")

                // [recruitment] was read (and this command applied to it) against a snapshot version that
                // may now be stale - e.g. a self-signup append() may have committed a new participant
                // since. Its fromSnapshotVersion is exactly what AggregateRoot's doc says a repository
                // write should verify against ("this value should be used to verify whether the expected
                // version is edited"). lockAndGetVersion locks the row first, so this check and the write
                // below are atomic with respect to any concurrent append()/updateRecruitment() for the same
                // recruitment - closing the check-then-act gap, not just detecting it after the fact. On a
                // mismatch, replace()'s diff would otherwise silently DELETE rows it has no way of knowing
                // about; failing loudly here lets the caller retry instead of losing data.
                //
                // fromSnapshotVersion is only null for an aggregate never loaded via fromSnapshot(...); every
                // real caller of updateRecruitment loads through fromSnapshot first, so this should never be
                // null here. checkNotNull fails loudly if that ever changes, rather than silently skipping
                // the whole guard (a fail-open null check would let replace() overwrite unconditionally).
                val expectedVersion =
                    checkNotNull(recruitment.fromSnapshotVersion) {
                        "Recruitment for study $studyId has no snapshot version; refusing to update without one."
                    }
                val currentVersion = normalizationStore.lockAndGetVersion(recruitmentFound.id)
                if (currentVersion != expectedVersion) {
                    throw ConflictException(
                        "Recruitment for study $studyId was modified concurrently (expected version " +
                            "$expectedVersion, found $currentVersion); refusing to overwrite.",
                    )
                }

                val normalized = RecruitmentNormalizer.decompose(recruitment.getSnapshot())
                recruitmentFound.snapshot = normalized.envelopeSnapshot
                recruitmentRepository.save(recruitmentFound)
                normalizationStore.replace(recruitmentFound.id, normalized)
                LOGGER.info("Recruitment with studyId $studyId is updated.")
            }
        }

    /**
     * Rebuilds the recruitment from the normalized tables plus the envelope carried in the (now small)
     * `snapshot` blob. The `RecruitmentNormalizer` round-trip is proven serialization-lossless.
     */
    private fun reconstructFromTables(recruitment: Recruitment): CoreRecruitment {
        val envelope = WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), recruitment.snapshot!!)
        val rows = normalizationStore.readRows(recruitment.id)
        val snapshot =
            RecruitmentNormalizer.reconstruct(
                RecruitmentNormalizer.decompose(envelope)
                    .copy(participants = rows.participants, groups = rows.groups, members = rows.members),
            )
        return CoreRecruitment.fromSnapshot(snapshot)
    }
}
