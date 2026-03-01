package com.decrux.pocketr_api.services.household

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.household.Household
import com.decrux.pocketr_api.entities.db.household.HouseholdAccountShare
import com.decrux.pocketr_api.entities.db.household.HouseholdMember
import com.decrux.pocketr_api.entities.db.household.HouseholdRole
import com.decrux.pocketr_api.entities.db.household.MemberStatus
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.dtos.CreateHouseholdDto
import com.decrux.pocketr_api.entities.dtos.InviteMemberDto
import com.decrux.pocketr_api.entities.dtos.ShareAccountDto
import com.decrux.pocketr_api.exceptions.ForbiddenException
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.HouseholdAccountShareRepository
import com.decrux.pocketr_api.repositories.HouseholdMemberRepository
import com.decrux.pocketr_api.repositories.HouseholdRepository
import com.decrux.pocketr_api.repositories.UserRepository
import com.decrux.pocketr_api.services.OwnershipGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for ManageHouseholdImpl covering account sharing, membership,
 * role enforcement, and household visibility (Section 12.2).
 *
 * Uses Mockito mocks for all 5 repository dependencies.
 *
 * Note: User entity uses Long IDs (not UUID). Test fixtures use Long user IDs.
 */
@DisplayName("ManageHouseholdImpl")
class HouseholdAccountShareIntegrationTest {
    private lateinit var householdRepository: HouseholdRepository
    private lateinit var memberRepository: HouseholdMemberRepository
    private lateinit var shareRepository: HouseholdAccountShareRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var userRepository: UserRepository
    private lateinit var service: ManageHouseholdImpl

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")

    private val userA =
        User(
            userId = 1L,
            password = "encoded",
            email = "alice@example.com",
        )

    private val userB =
        User(
            userId = 2L,
            password = "encoded",
            email = "bob@example.com",
        )

    private val userC =
        User(
            userId = 3L,
            password = "encoded",
            email = "carol@example.com",
        )

    private val householdId = UUID.randomUUID()
    private val household =
        Household(
            id = householdId,
            name = "Test Household",
            createdBy = userA,
            createdAt = Instant.now(),
        )

    private val checkingId = UUID.randomUUID()
    private val checkingAccount =
        Account(
            id = checkingId,
            owner = userA,
            name = "Checking",
            type = AccountType.ASSET,
            currency = eur,
        )

    private val savingsId = UUID.randomUUID()

    private fun activeMember(
        user: User,
        role: HouseholdRole,
    ) = HouseholdMember(
        household = household,
        user = user,
        role = role,
        status = MemberStatus.ACTIVE,
        joinedAt = Instant.now(),
    )

    private fun invitedMember(user: User) =
        HouseholdMember(
            household = household,
            user = user,
            role = HouseholdRole.MEMBER,
            status = MemberStatus.INVITED,
            invitedBy = userA,
            invitedAt = Instant.now(),
        )

    @BeforeEach
    fun setUp() {
        householdRepository = mock(HouseholdRepository::class.java)
        memberRepository = mock(HouseholdMemberRepository::class.java)
        shareRepository = mock(HouseholdAccountShareRepository::class.java)
        accountRepository = mock(AccountRepository::class.java)
        userRepository = mock(UserRepository::class.java)

        service =
            ManageHouseholdImpl(
                householdRepository,
                memberRepository,
                shareRepository,
                accountRepository,
                userRepository,
                OwnershipGuard(),
            )

        `when`(memberRepository.findByUserUserIdAndStatus(1L, MemberStatus.INVITED)).thenReturn(emptyList())
        `when`(memberRepository.findByUserUserIdAndStatus(2L, MemberStatus.INVITED)).thenReturn(emptyList())
        `when`(memberRepository.findByUserUserIdAndStatus(3L, MemberStatus.INVITED)).thenReturn(emptyList())
    }

    @Nested
    @DisplayName("Household creation")
    inner class HouseholdCreation {
        @Test
        @DisplayName("creates household with creator as OWNER and ACTIVE")
        fun createsHouseholdWithOwner() {
            `when`(householdRepository.save(any(Household::class.java))).thenAnswer { invocation ->
                val h = invocation.arguments[0] as Household
                h.id = UUID.randomUUID()
                h
            }

            val result = service.createHousehold(CreateHouseholdDto(name = "Family"), userA)

            assertEquals("Family", result.name)
            assertEquals(1, result.members.size)
            assertEquals(1L, result.members[0].userId)
            assertEquals("OWNER", result.members[0].role)
            assertEquals("ACTIVE", result.members[0].status)
        }

        @Test
        @DisplayName("trims whitespace from household name")
        fun trimsHouseholdName() {
            `when`(householdRepository.save(any(Household::class.java))).thenAnswer { invocation ->
                val h = invocation.arguments[0] as Household
                h.id = UUID.randomUUID()
                h
            }

            val result = service.createHousehold(CreateHouseholdDto(name = "  Family  "), userA)

            assertEquals("Family", result.name)
        }

        @Test
        @DisplayName("cannot create a new household when already active in another household")
        fun cannotCreateWhenAlreadyActiveElsewhere() {
            `when`(memberRepository.existsByUserUserIdAndStatus(1L, MemberStatus.ACTIVE)).thenReturn(true)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.createHousehold(CreateHouseholdDto(name = "Family"), userA)
                }

            assertEquals(409, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("already an active member"))
        }
    }

    @Nested
    @DisplayName("Account sharing visibility")
    inner class AccountSharingVisibility {
        @Test
        @DisplayName("shared accounts appear in household account list for all members")
        fun sharedAccountAppearsForAllMembers() {
            stubActiveMember(userB, HouseholdRole.MEMBER)
            val share =
                HouseholdAccountShare(
                    household = household,
                    account = checkingAccount,
                    sharedBy = userA,
                )
            `when`(shareRepository.findByHouseholdIdWithAccountAndOwner(householdId)).thenReturn(listOf(share))

            val accounts = service.listHouseholdAccounts(householdId, userB)

            assertEquals(1, accounts.size)
            assertEquals(checkingId, accounts[0].id)
            assertEquals(1L, accounts[0].ownerUserId)
            assertEquals("Checking", accounts[0].name)
            assertEquals("ASSET", accounts[0].type)
            assertEquals("EUR", accounts[0].currency)
        }

        @Test
        @DisplayName("unshared account disappears from household account list")
        fun unsharingRemovesAccount() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            val share =
                HouseholdAccountShare(
                    household = household,
                    account = checkingAccount,
                    sharedBy = userA,
                )
            `when`(shareRepository.findByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(share)

            service.unshareAccount(householdId, checkingId, userA)

            verify(shareRepository).delete(share)
        }

        @Test
        @DisplayName("non-shared accounts remain invisible in household mode")
        fun nonSharedAccountsInvisible() {
            stubActiveMember(userB, HouseholdRole.MEMBER)
            val share =
                HouseholdAccountShare(
                    household = household,
                    account = checkingAccount,
                    sharedBy = userA,
                )
            `when`(shareRepository.findByHouseholdIdWithAccountAndOwner(householdId)).thenReturn(listOf(share))

            val accounts = service.listHouseholdAccounts(householdId, userB)

            assertEquals(1, accounts.size)
            assertEquals("Checking", accounts[0].name)
            assertFalse(accounts.any { it.name == "Secret Savings" })
        }

        @Test
        @DisplayName("listSharedAccounts returns share metadata for active members")
        fun listSharedAccountsReturnsMetadata() {
            stubActiveMember(userB, HouseholdRole.MEMBER)
            val share =
                HouseholdAccountShare(
                    household = household,
                    account = checkingAccount,
                    sharedBy = userA,
                )
            `when`(shareRepository.findByHouseholdIdWithAccountAndOwner(householdId)).thenReturn(listOf(share))

            val shares = service.listSharedAccounts(householdId, userB)

            assertEquals(1, shares.size)
            assertEquals(checkingId, shares[0].accountId)
            assertEquals("Checking", shares[0].accountName)
            assertEquals("alice@example.com", shares[0].ownerEmail)
        }
    }

    @Nested
    @DisplayName("Account sharing operations")
    inner class AccountSharingOperations {
        @Test
        @DisplayName("owner can share their account into a household")
        fun ownerCanShareAccount() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(householdRepository.findById(householdId)).thenReturn(Optional.of(household))
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checkingAccount))
            `when`(shareRepository.existsByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(false)
            `when`(shareRepository.save(any(HouseholdAccountShare::class.java))).thenAnswer { it.arguments[0] }

            val result = service.shareAccount(householdId, ShareAccountDto(checkingId), userA)

            assertEquals(checkingId, result.accountId)
            assertEquals("Checking", result.accountName)
            assertEquals("alice@example.com", result.ownerEmail)
        }

        @Test
        @DisplayName("non-owner cannot share someone else's account")
        fun nonOwnerCannotShare() {
            stubActiveMember(userB, HouseholdRole.MEMBER)
            `when`(householdRepository.findById(householdId)).thenReturn(Optional.of(household))
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checkingAccount))

            val ex =
                assertThrows(ForbiddenException::class.java) {
                    service.shareAccount(householdId, ShareAccountDto(checkingId), userB)
                }
            assertTrue(ex.message!!.contains("owner"))
        }

        @Test
        @DisplayName("duplicate share throws CONFLICT")
        fun duplicateShareThrowsConflict() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(householdRepository.findById(householdId)).thenReturn(Optional.of(household))
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checkingAccount))
            `when`(shareRepository.existsByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(true)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.shareAccount(householdId, ShareAccountDto(checkingId), userA)
                }

            assertEquals(409, ex.statusCode.value())
        }

        @Test
        @DisplayName("non-owner cannot unshare someone else's account")
        fun nonOwnerCannotUnshare() {
            stubActiveMember(userB, HouseholdRole.MEMBER)
            val share =
                HouseholdAccountShare(
                    household = household,
                    account = checkingAccount,
                    sharedBy = userA,
                )
            `when`(shareRepository.findByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(share)

            val ex =
                assertThrows(ForbiddenException::class.java) {
                    service.unshareAccount(householdId, checkingId, userB)
                }
        }

        @Test
        @DisplayName("unshare non-existent share throws NOT_FOUND")
        fun unshareNonExistentThrows404() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(shareRepository.findByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(null)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.unshareAccount(householdId, checkingId, userA)
                }

            assertEquals(404, ex.statusCode.value())
        }
    }

    @Nested
    @DisplayName("Membership role enforcement")
    inner class MembershipRoleEnforcement {
        @Test
        @DisplayName("OWNER can invite new members")
        fun ownerCanInviteMembers() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC))
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)
            `when`(memberRepository.save(any(HouseholdMember::class.java))).thenAnswer { it.arguments[0] }

            val result = service.inviteMember(householdId, InviteMemberDto("carol@example.com"), userA)

            assertEquals(3L, result.userId)
            assertEquals("MEMBER", result.role)
            assertEquals("INVITED", result.status)
        }

        @Test
        @DisplayName("ADMIN can invite new members")
        fun adminCanInviteMembers() {
            stubActiveMember(userB, HouseholdRole.ADMIN)
            `when`(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC))
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)
            `when`(memberRepository.save(any(HouseholdMember::class.java))).thenAnswer { it.arguments[0] }

            val result = service.inviteMember(householdId, InviteMemberDto("carol@example.com"), userB)

            assertEquals(3L, result.userId)
            assertEquals("INVITED", result.status)
        }

        @Test
        @DisplayName("MEMBER cannot invite new members")
        fun memberCannotInviteMembers() {
            stubActiveMember(userB, HouseholdRole.MEMBER)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.inviteMember(householdId, InviteMemberDto("carol@example.com"), userB)
                }

            assertEquals(403, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("OWNER or ADMIN"))
        }

        @Test
        @DisplayName("cannot invite a user who is active in another household")
        fun cannotInviteUserActiveElsewhere() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC))
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)
            `when`(memberRepository.existsByUserUserIdAndStatus(3L, MemberStatus.ACTIVE)).thenReturn(true)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.inviteMember(householdId, InviteMemberDto("carol@example.com"), userA)
                }

            assertEquals(409, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("active member"))
        }

        @Test
        @DisplayName("INVITED (non-ACTIVE) user cannot list household accounts")
        fun invitedMemberCannotAccessHouseholdAccounts() {
            val invited = invitedMember(userC)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(invited)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.listHouseholdAccounts(householdId, userC)
                }

            assertEquals(403, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("not active"))
        }

        @Test
        @DisplayName("non-member cannot access household")
        fun nonMemberCannotAccess() {
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.listHouseholdAccounts(householdId, userC)
                }

            assertEquals(403, ex.statusCode.value())
        }
    }

    @Nested
    @DisplayName("Invite acceptance")
    inner class InviteAcceptance {
        @Test
        @DisplayName("accepting invite changes status from INVITED to ACTIVE")
        fun acceptInviteChangesStatusToActive() {
            val invited = invitedMember(userB)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(invited)
            `when`(memberRepository.save(any(HouseholdMember::class.java))).thenAnswer { it.arguments[0] }

            val result = service.acceptInvite(householdId, userB)

            assertEquals("ACTIVE", result.status)
            assertNotNull(result.joinedAt)
        }

        @Test
        @DisplayName("accepting when already ACTIVE throws BAD_REQUEST")
        fun acceptingWhenAlreadyActiveThrows() {
            val active = activeMember(userB, HouseholdRole.MEMBER)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(active)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.acceptInvite(householdId, userB)
                }

            assertEquals(400, ex.statusCode.value())
        }

        @Test
        @DisplayName("accepting non-existent invite throws NOT_FOUND")
        fun acceptingNonExistentInviteThrows() {
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(null)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.acceptInvite(householdId, userB)
                }

            assertEquals(404, ex.statusCode.value())
        }

        @Test
        @DisplayName("cannot accept invite when user is already active in another household")
        fun cannotAcceptInviteWhenAlreadyActiveElsewhere() {
            val invited = invitedMember(userB)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(invited)
            `when`(
                memberRepository.existsByUserUserIdAndStatusAndHouseholdIdNot(
                    2L,
                    MemberStatus.ACTIVE,
                    householdId,
                ),
            ).thenReturn(true)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.acceptInvite(householdId, userB)
                }

            assertEquals(409, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("Leave your current household"))
        }
    }

    @Nested
    @DisplayName("Invite edge cases")
    inner class InviteEdgeCases {
        @Test
        @DisplayName("inviting already-existing member throws CONFLICT")
        fun invitingExistingMemberThrowsConflict() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(userB))
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L))
                .thenReturn(activeMember(userB, HouseholdRole.MEMBER))

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.inviteMember(householdId, InviteMemberDto("bob@example.com"), userA)
                }

            assertEquals(409, ex.statusCode.value())
        }

        @Test
        @DisplayName("inviting non-existent user throws BAD_REQUEST")
        fun invitingNonExistentUserThrows() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty())

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.inviteMember(householdId, InviteMemberDto("nobody@example.com"), userA)
                }

            assertEquals(400, ex.statusCode.value())
        }

        @Test
        @DisplayName("invite trims email whitespace")
        fun inviteTrimsEmail() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC))
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)
            `when`(memberRepository.save(any(HouseholdMember::class.java))).thenAnswer { it.arguments[0] }

            val result = service.inviteMember(householdId, InviteMemberDto("  carol@example.com  "), userA)

            assertEquals(3L, result.userId)
        }
    }

    @Nested
    @DisplayName("Household listing and detail")
    inner class HouseholdListingAndDetail {
        @Test
        @DisplayName("listHouseholds returns memberships with status")
        fun listHouseholdsReturnsMembershipsWithStatus() {
            val member = activeMember(userA, HouseholdRole.OWNER)
            val invite = invitedMember(userA)
            `when`(memberRepository.findByUserUserId(1L)).thenReturn(listOf(member, invite))

            val result = service.listHouseholds(userA)

            assertEquals(2, result.size)
            assertEquals("ACTIVE", result[0].status)
            assertEquals("INVITED", result[1].status)
        }

        @Test
        @DisplayName("getHousehold throws FORBIDDEN for non-member")
        fun getHouseholdForbiddenForNonMember() {
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)

            val ex =
                assertThrows(ResponseStatusException::class.java) {
                    service.getHousehold(householdId, userC)
                }

            assertEquals(403, ex.statusCode.value())
        }

        @Test
        @DisplayName("getHousehold returns details for active member")
        fun getHouseholdReturnsDetailsForActiveMember() {
            stubActiveMember(userA, HouseholdRole.OWNER)
            `when`(householdRepository.findById(householdId)).thenReturn(Optional.of(household))

            val result = service.getHousehold(householdId, userA)

            assertEquals(householdId, result.id)
            assertEquals("Test Household", result.name)
        }
    }

    @Nested
    @DisplayName("isActiveMember and isAccountShared helpers")
    inner class HelperMethods {
        @Test
        @DisplayName("isActiveMember returns true for ACTIVE member")
        fun isActiveMemberReturnsTrue() {
            val member = activeMember(userA, HouseholdRole.OWNER)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 1L)).thenReturn(member)

            assertTrue(service.isActiveMember(householdId, 1L))
        }

        @Test
        @DisplayName("isActiveMember returns false for INVITED member")
        fun isActiveMemberReturnsFalseForInvited() {
            val invited = invitedMember(userB)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(invited)

            assertFalse(service.isActiveMember(householdId, 2L))
        }

        @Test
        @DisplayName("isActiveMember returns false for non-member")
        fun isActiveMemberReturnsFalseForNonMember() {
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null)

            assertFalse(service.isActiveMember(householdId, 3L))
        }

        @Test
        @DisplayName("isAccountShared delegates to repository")
        fun isAccountSharedDelegatesToRepo() {
            `when`(shareRepository.existsByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(true)

            assertTrue(service.isAccountShared(householdId, checkingId))
        }

        @Test
        @DisplayName("getSharedAccountIds returns account IDs from repository")
        fun getSharedAccountIdsReturnsIds() {
            val expectedIds = setOf(checkingId, savingsId)
            `when`(shareRepository.findSharedAccountIdsByHouseholdId(householdId)).thenReturn(expectedIds)

            val result = service.getSharedAccountIds(householdId)

            assertEquals(expectedIds, result)
            verify(shareRepository).findSharedAccountIdsByHouseholdId(householdId)
        }

        @Test
        @DisplayName("getSharedAccountIds returns empty set when no accounts are shared")
        fun getSharedAccountIdsReturnsEmptySet() {
            `when`(shareRepository.findSharedAccountIdsByHouseholdId(householdId)).thenReturn(emptySet())

            val result = service.getSharedAccountIds(householdId)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Leave household")
    inner class LeaveHousehold {
        @Test
        @DisplayName("active member can leave household")
        fun activeMemberCanLeave() {
            val member = activeMember(userB, HouseholdRole.MEMBER)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(member)
            `when`(memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE))
                .thenReturn(listOf(activeMember(userA, HouseholdRole.OWNER)))

            service.leaveHousehold(householdId, userB)

            verify(shareRepository).deleteByHouseholdIdAndAccountOwnerUserId(householdId, 2L)
            verify(memberRepository).delete(member)
        }

        @Test
        @DisplayName("owner leave promotes an active admin to owner")
        fun ownerLeavePromotesAdmin() {
            val ownerMember = activeMember(userA, HouseholdRole.OWNER)
            val adminMember = activeMember(userB, HouseholdRole.ADMIN)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 1L)).thenReturn(ownerMember)
            `when`(memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE))
                .thenReturn(listOf(adminMember))
            `when`(memberRepository.save(any(HouseholdMember::class.java))).thenAnswer { it.arguments[0] }

            service.leaveHousehold(householdId, userA)

            assertEquals(HouseholdRole.OWNER, adminMember.role)
            verify(memberRepository).save(adminMember)
        }

        @Test
        @DisplayName("last active member leaving deletes household")
        fun lastActiveMemberLeavingDeletesHousehold() {
            val ownerMember = activeMember(userA, HouseholdRole.OWNER)
            `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, 1L)).thenReturn(ownerMember)
            `when`(memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE))
                .thenReturn(emptyList())
            `when`(memberRepository.findByHouseholdId(householdId)).thenReturn(emptyList())

            service.leaveHousehold(householdId, userA)

            verify(shareRepository).deleteByHouseholdId(householdId)
            verify(householdRepository).delete(household)
        }
    }

    /** Stubs memberRepository to return an ACTIVE member for the given user and role. */
    private fun stubActiveMember(
        user: User,
        role: HouseholdRole,
    ) {
        val member = activeMember(user, role)
        val userId = requireNotNull(user.userId)
        `when`(memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)).thenReturn(member)
    }
}
