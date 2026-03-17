package dk.cachet.carp.webservices.account.service

import dk.cachet.carp.common.application.EmailAddress
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.webservices.account.service.impl.AccountServiceImpl
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.MagicLinkResponse
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.RequiredActions
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.web.reactive.function.client.WebClientResponseException
import kotlin.test.*

class AccountServiceImplTest {
    private val issuerFacade: IssuerFacade = mockk()

    @Nested
    inner class Invite {
        @Test
        fun `should not create an account if one exists`() =
            runTest {
                val accountIdentity = EmailAccountIdentity(EmailAddress("test@test.com"))
                val invitedAs = Role.RESEARCHER

                val foundAccount = mockk<Account>()
                every { foundAccount getProperty "role" } answers { callOriginal() }
                every { foundAccount setProperty "role" value any<Role>() } answers { callOriginal() }
                every { foundAccount getProperty "email" } answers { callOriginal() }

                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns foundAccount
                coEvery { issuerFacade.createAccount(any()) } returns foundAccount
                coEvery { issuerFacade.addRole(any(), any()) } just runs

                val sut = AccountServiceImpl(issuerFacade)

                val invitedAccount = sut.invite(accountIdentity, invitedAs)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                coVerify(exactly = 0) { issuerFacade.createAccount(any()) }
                coVerify(exactly = 1) { issuerFacade.addRole(foundAccount, invitedAs) }
                expect(invitedAs) { invitedAccount.role }
            }

        @Test
        fun `should create an account if none exists`() =
            runTest {
                val accountIdentity = EmailAccountIdentity(EmailAddress("test@test.com"))
                val invitedAs = Role.RESEARCHER

                val foundAccount = null
                val createdAccount = mockk<Account>()
                every { createdAccount getProperty "role" } answers { callOriginal() }
                every { createdAccount setProperty "role" value any<Role>() } answers { callOriginal() }
                every { createdAccount getProperty "email" } answers { callOriginal() }

                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns foundAccount
                coEvery { issuerFacade.createAccount(any()) } returns createdAccount
                coEvery { issuerFacade.addRole(any(), any()) } just runs

                val sut = AccountServiceImpl(issuerFacade)

                val invitedAccount = sut.invite(accountIdentity, invitedAs)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                coVerify(exactly = 1) { issuerFacade.createAccount(any()) }
                coVerify(exactly = 1) { issuerFacade.addRole(any(), invitedAs) }
                expect(invitedAs) { invitedAccount.role }
            }

        @Test
        fun `should send an invitation if the account has an email`() =
            runTest {
                val email = "test@test.com"
                val accountIdentity = EmailAccountIdentity(EmailAddress(email))
                val account = mockk<Account>()

                every { account setProperty "role" value any<Role>() } answers { callOriginal() }
                every { account.email } returns email

                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                coEvery { issuerFacade.createAccount(any()) } returns account
                coEvery { issuerFacade.addRole(any(), any()) } just runs
                coEvery { issuerFacade.executeActions(any(), any(), any()) } just runs

                val sut = AccountServiceImpl(issuerFacade)

                sut.invite(accountIdentity, Role.PARTICIPANT)

                coVerify(exactly = 1) { issuerFacade.executeActions(account, null, RequiredActions.forNewAccounts) }
            }

        @Test
        fun `should not send an invitation if the account has no email`() =
            runTest {
                val email = " "
                val accountIdentity = EmailAccountIdentity(EmailAddress(email))
                val account = mockk<Account>()

                every { account setProperty "role" value any<Role>() } answers { callOriginal() }
                every { account.email } returns email

                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                coEvery { issuerFacade.createAccount(any()) } returns account
                coEvery { issuerFacade.addRole(any(), any()) } just runs
                coEvery { issuerFacade.executeActions(any(), any(), any()) } just runs

                val sut = AccountServiceImpl(issuerFacade)

                sut.invite(accountIdentity, Role.PARTICIPANT)

                coVerify(exactly = 0) { issuerFacade.executeActions(any(), any(), any()) }
            }

        @Test
        fun `should not throw if sending the email with actions fails`() =
            runTest {
                val email = "test@test.com"
                val accountIdentity = EmailAccountIdentity(EmailAddress(email))
                val account = mockk<Account>()

                every { account setProperty "role" value any<Role>() } answers { callOriginal() }
                every { account.email } returns email

                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                coEvery { issuerFacade.createAccount(any()) } returns account
                coEvery { issuerFacade.addRole(any(), any()) } just runs
                coEvery { issuerFacade.executeActions(any(), any(), any()) } throws mockk<WebClientResponseException>()

                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.invite(accountIdentity, Role.PARTICIPANT)

                coVerify(exactly = 1) { issuerFacade.executeActions(account, null, RequiredActions.forNewAccounts) }
                expect(account) { result }
            }
    }

    @Nested
    inner class FindByUUID {
        @Test
        fun `should relay the task to issuerFacade`() =
            runTest {
                val uuid = UUID.randomUUID()
                val account = mockk<Account>()
                coEvery { issuerFacade.getAccount(any<UUID>()) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.findByUUID(uuid)

                coVerify(exactly = 1) { issuerFacade.getAccount(uuid) }
                expect(account) { result }
            }

        @Test
        fun `should return null if exception occurred`() =
            runTest {
                val uuid = UUID.randomUUID()
                coEvery { issuerFacade.getAccount(any<UUID>()) } throws Exception()
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.findByUUID(uuid)

                coVerify(exactly = 1) { issuerFacade.getAccount(uuid) }
                expect(null) { result }
            }
    }

    @Nested
    inner class FindByAccountIdentity {
        @Test
        fun `should relay the task to issuerFacade`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val account = mockk<Account>()
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.findByAccountIdentity(accountIdentity)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                expect(account) { result }
            }

        @Test
        fun `should return null if exception occurred`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } throws Exception()
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.findByAccountIdentity(accountIdentity)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                expect(null) { result }
            }
    }

    @Nested
    inner class FindAllByClaim {
        @Test
        fun `should return accounts by role`() =
            runTest {
                val claim = mockk<Claim>()
                val mockAccounts = listOf(mockk<Account>())
                coEvery { issuerFacade.getAllByClaim(any()) } returns mockAccounts
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.findAllByClaim(claim)

                coVerify(exactly = 1) { issuerFacade.getAllByClaim(claim) }
                expect(mockAccounts) { result }
            }

        @Test
        fun `should return empty collection if an exception occurs`() =
            runTest {
                val claim = mockk<Claim>()
                coEvery { issuerFacade.getAllByClaim(any()) } throws Exception()
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.findAllByClaim(claim)

                coVerify(exactly = 1) { issuerFacade.getAllByClaim(claim) }
                expect(emptyList()) { result }
            }
    }

    @Nested
    inner class HasRoleByEmail {
        @Test
        fun `should relay the task to issuerFacade`() =
            runTest {
                val emailAddress =
                    mockk<EmailAddress> {
                        every { address } returns ""
                    }
                val shouldNotHave = Role.RESEARCHER
                val shouldHave = Role.PARTICIPANT
                val account = mockk<Account>()
                every { account getProperty "role" } answers { Role.PARTICIPANT }
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val firstCall = sut.hasRoleByEmail(emailAddress, shouldNotHave)
                val secondCall = sut.hasRoleByEmail(emailAddress, shouldHave)

                coVerify(exactly = 2) { issuerFacade.getAccount(any<AccountIdentity>()) }
                expect(false) { firstCall }
                expect(true) { secondCall }
            }

        @Test
        fun `should throw if account is null`() =
            runTest {
                val emailAddress =
                    mockk<EmailAddress> {
                        every { address } returns ""
                    }
                val role = Role.RESEARCHER
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                val sut = AccountServiceImpl(issuerFacade)

                assertFailsWith<IllegalArgumentException> {
                    sut.hasRoleByEmail(emailAddress, role)
                }

                coVerify(exactly = 1) { issuerFacade.getAccount(any<AccountIdentity>()) }
            }
    }

    @Nested
    inner class AddRole {
        @Test
        fun `should throw if there isn't a matching account in the issuerFacade`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val role = Role.RESEARCHER
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                coEvery { issuerFacade.addRole(any(), any()) } just runs
                val sut = AccountServiceImpl(issuerFacade)

                assertFailsWith<IllegalArgumentException> {
                    sut.addRole(accountIdentity, role)
                }

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                coVerify(exactly = 0) { issuerFacade.addRole(any(), any()) }
            }

        @Test
        fun `should add role to the found account`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val role = Role.RESEARCHER
                val mockAccount = mockk<Account>()
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns mockAccount
                coEvery { issuerFacade.addRole(any(), any()) } just runs
                val sut = AccountServiceImpl(issuerFacade)

                sut.addRole(accountIdentity, role)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                coVerify(exactly = 1) { issuerFacade.addRole(mockAccount, role) }
            }
    }

    @Nested
    inner class Grant {
        @Test
        fun `should throw if there is any VirtualClaims`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims =
                    setOf(
                        Claim.FileOwner(1),
                        Claim.ManageDeployment(UUID.randomUUID()),
                    )
                val sut = AccountServiceImpl(issuerFacade)

                assertFailsWith<UnsupportedOperationException> {
                    sut.grant(accountIdentity, claims)
                }
            }

        @Test
        fun `should throw if any of the returned accounts is null`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims = setOf(Claim.FileOwner(1))
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                val sut = AccountServiceImpl(issuerFacade)

                assertFailsWith<IllegalArgumentException> {
                    sut.grant(accountIdentity, claims)
                }
            }

        @Test
        fun `should grant the claims to the account and return the updated account 1`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims = setOf(Claim.FileOwner(1), Claim.FileOwner(2))
                val account =
                    Account(
                        id = "id",
                        carpClaims = emptySet(),
                    )
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns account
                coEvery { issuerFacade.updateAccount(account) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.grant(accountIdentity, claims)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                assertEquals(account.carpClaims, claims)
                assertEquals(result.carpClaims?.size, 2)
            }

        @Test
        fun `should grant the claims to the account and return the updated account 2`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims = setOf(Claim.FileOwner(1), Claim.FileOwner(2))
                val account =
                    Account(
                        id = "id",
                        carpClaims = setOf(Claim.FileOwner(3)),
                    )
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns account
                coEvery { issuerFacade.updateAccount(account) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.grant(accountIdentity, claims)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                assertEquals(account.carpClaims, setOf(Claim.FileOwner(3), Claim.FileOwner(1), Claim.FileOwner(2)))
                assertEquals(result.carpClaims?.size, 3)
            }
    }

    @Nested
    inner class Revoke {
        @Test
        fun `should throw if there is any VirtualClaims`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims =
                    setOf(
                        Claim.FileOwner(1),
                        Claim.ManageDeployment(UUID.randomUUID()),
                    )
                val sut = AccountServiceImpl(issuerFacade)

                assertFailsWith<UnsupportedOperationException> {
                    sut.revoke(accountIdentity, claims)
                }
            }

        @Test
        fun `should throw if any of the returned accounts is null`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims = setOf(Claim.FileOwner(1))
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns null
                val sut = AccountServiceImpl(issuerFacade)

                assertFailsWith<IllegalArgumentException> {
                    sut.revoke(accountIdentity, claims)
                }
            }

        @Test
        fun `should revoke the claims from the account and return the updated account 1`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims = setOf(Claim.FileOwner(1), Claim.FileOwner(2))
                val account =
                    Account(
                        id = "id",
                        carpClaims = setOf(Claim.FileOwner(1), Claim.FileOwner(2), Claim.FileOwner(3)),
                    )
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns account
                coEvery { issuerFacade.updateAccount(account) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.revoke(accountIdentity, claims)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                assertEquals(account.carpClaims, setOf(Claim.FileOwner(3)))
                assertEquals(result.carpClaims?.size, 1)
            }

        @Test
        fun `should revoke the claims from the account and return the updated account 2`() =
            runTest {
                val accountIdentity = mockk<AccountIdentity>()
                val claims = setOf(Claim.FileOwner(1), Claim.FileOwner(2))
                val account =
                    Account(
                        id = "id",
                        carpClaims = emptySet(),
                    )
                coEvery { issuerFacade.getAccount(any<AccountIdentity>()) } returns account
                coEvery { issuerFacade.updateAccount(account) } returns account
                val sut = AccountServiceImpl(issuerFacade)

                val result = sut.revoke(accountIdentity, claims)

                coVerify(exactly = 1) { issuerFacade.getAccount(accountIdentity) }
                assertEquals(account.carpClaims, emptySet())
                assertEquals(result.carpClaims?.size, 0)
            }
    }

    @Nested
    inner class GenerateAnonymousAccount {
        @Test
        fun `should return a pair of UsernameAccountIdentity and a password`() =
            runTest {
                val expirationSeconds = 3600L
                val clientId = "clientId"
                val redirectUri = "http://localhost:8080"
                val account = mockk<Account>()
                coEvery { account.username } returns UUID.randomUUID().toString()
                coEvery {
                    issuerFacade
                        .createAnonymousAccountsBulk(
                            1,
                            expirationSeconds,
                            clientId,
                            redirectUri,
                            null,
                            null,
                        )
                } returns flowOf(MagicLinkResponse(account.username, "random-link-placeholder"))
                val sut = AccountServiceImpl(issuerFacade)

                val result = mutableListOf<Pair<String, String>>()
                sut.generateAnonymousAccountBulk(expirationSeconds, clientId, redirectUri, null, 1, null)
                    .collect { result.add(Pair(it.userId!!, it.link!!)) }

                assertTrue { result.first().first.length == UUID.randomUUID().stringRepresentation.length }
            }
    }
}
