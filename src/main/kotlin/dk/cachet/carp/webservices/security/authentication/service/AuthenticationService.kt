package dk.cachet.carp.webservices.security.authentication.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role

interface AuthenticationService {
    fun getId(): UUID

    fun getRole(): Role

    /** The claims held directly by the current user (as present in the JWT), without any derivation. */
    fun getClaims(): Collection<Claim>

    /**
     * Whether the current user holds [claim].
     *
     * For [Claim.InDeployment] this also returns `true` when the user manages the study the deployment
     * belongs to (via [Claim.ManageStudy]/[Claim.LimitedManageStudy]), resolved through a single indexed
     * lookup instead of expanding a manage claim into every deployment of the study.
     */
    fun hasClaim(claim: Claim): Boolean

    fun getCarpIdentity(): AccountIdentity
}
