package dk.cachet.carp.webservices.security.authentication.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationServiceImplTest {
    private val participantRepository: CoreParticipantRepository = mockk()
    private val sut = AuthenticationServiceImpl(participantRepository)

    @AfterTest
    fun tearDown() = SecurityContextHolder.clearContext()

    /**
     * Authenticates the current thread with a JWT carrying [authorities]. Authorities follow the
     * `token_claim_{value}` scheme that [Claim.fromGrantedAuthority] parses back into claims, e.g.
     * `manage_study_{uuid}` or `in_deployment_{uuid}`.
     */
    private fun authenticateWith(vararg authorities: String) {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().stringRepresentation)
                .build()
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt, authorities.map { SimpleGrantedAuthority(it) })
    }

    private fun manageStudy(studyId: UUID) = "manage_study_${studyId.stringRepresentation}"

    private fun limitedManageStudy(studyId: UUID) = "limited_manage_study_${studyId.stringRepresentation}"

    private fun inDeployment(deploymentId: UUID) = "in_deployment_${deploymentId.stringRepresentation}"

    @Test
    fun `participant with an explicit in-deployment claim passes without any study lookup`() {
        val deploymentId = UUID.randomUUID()
        authenticateWith(inDeployment(deploymentId))

        assertTrue(sut.hasClaim(Claim.InDeployment(deploymentId)))
        verify(exactly = 0) { participantRepository.getStudyIdByDeploymentId(any()) }
    }

    @Test
    fun `study manager passes in-deployment check for a deployment in the managed study`() {
        val studyId = UUID.randomUUID()
        val deploymentId = UUID.randomUUID()
        authenticateWith(manageStudy(studyId))
        every { participantRepository.getStudyIdByDeploymentId(deploymentId) } returns studyId

        assertTrue(sut.hasClaim(Claim.InDeployment(deploymentId)))
    }

    @Test
    fun `limited study manager is treated like a manager for in-deployment checks`() {
        val studyId = UUID.randomUUID()
        val deploymentId = UUID.randomUUID()
        authenticateWith(limitedManageStudy(studyId))
        every { participantRepository.getStudyIdByDeploymentId(deploymentId) } returns studyId

        assertTrue(sut.hasClaim(Claim.InDeployment(deploymentId)))
    }

    @Test
    fun `study manager fails in-deployment check for a deployment in a different study`() {
        val managedStudyId = UUID.randomUUID()
        val otherStudyId = UUID.randomUUID()
        val deploymentId = UUID.randomUUID()
        authenticateWith(manageStudy(managedStudyId))
        every { participantRepository.getStudyIdByDeploymentId(deploymentId) } returns otherStudyId

        assertFalse(sut.hasClaim(Claim.InDeployment(deploymentId)))
    }

    @Test
    fun `study manager fails in-deployment check when the deployment resolves to no study`() {
        val studyId = UUID.randomUUID()
        val deploymentId = UUID.randomUUID()
        authenticateWith(manageStudy(studyId))
        every { participantRepository.getStudyIdByDeploymentId(deploymentId) } returns null

        assertFalse(sut.hasClaim(Claim.InDeployment(deploymentId)))
    }

    @Test
    fun `user without manage or in-deployment claims fails without any study lookup`() {
        val deploymentId = UUID.randomUUID()
        // A plain participant holds only a role authority, which maps to no claim.
        authenticateWith("ROLE_PARTICIPANT")

        assertFalse(sut.hasClaim(Claim.InDeployment(deploymentId)))
        verify(exactly = 0) { participantRepository.getStudyIdByDeploymentId(any()) }
    }

    @Test
    fun `non-deployment claims are matched by direct membership only`() {
        val studyId = UUID.randomUUID()
        authenticateWith(manageStudy(studyId))

        assertTrue(sut.hasClaim(Claim.ManageStudy(studyId)))
        assertFalse(sut.hasClaim(Claim.ManageStudy(UUID.randomUUID())))
        verify(exactly = 0) { participantRepository.getStudyIdByDeploymentId(any()) }
    }
}
