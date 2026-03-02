package com.decrux.pocketr.api.services.household;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.household.Household;
import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShare;
import com.decrux.pocketr.api.entities.db.household.HouseholdMember;
import com.decrux.pocketr.api.entities.db.household.HouseholdRole;
import com.decrux.pocketr.api.entities.db.household.MemberStatus;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateHouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdAccountShareDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdMemberDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdSummaryDto;
import com.decrux.pocketr.api.entities.dtos.InviteMemberDto;
import com.decrux.pocketr.api.entities.dtos.ShareAccountDto;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.HouseholdAccountShareRepository;
import com.decrux.pocketr.api.repositories.HouseholdMemberRepository;
import com.decrux.pocketr.api.repositories.HouseholdRepository;
import com.decrux.pocketr.api.repositories.UserRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ManageHouseholdImpl covering account sharing, membership,
 * role enforcement, and household visibility.
 */
@DisplayName("ManageHouseholdImpl")
class HouseholdAccountShareIntegrationTest {

    private HouseholdRepository householdRepository;
    private HouseholdMemberRepository memberRepository;
    private HouseholdAccountShareRepository shareRepository;
    private AccountRepository accountRepository;
    private UserRepository userRepository;
    private ManageHouseholdImpl service;

    private final Currency eur = new Currency("EUR", (short) 2, "Euro");

    private final User userA = buildUser(1L, "alice@example.com");
    private final User userB = buildUser(2L, "bob@example.com");
    private final User userC = buildUser(3L, "carol@example.com");

    private final UUID householdId = UUID.randomUUID();
    private final Household household = new Household(householdId, "Test Household", userA, Instant.now(), List.of());

    private final UUID checkingId = UUID.randomUUID();
    private final Account checkingAccount = new Account(
        checkingId,
        userA,
        "Checking",
        AccountType.ASSET,
        eur,
        Instant.now()
    );

    private final UUID savingsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        householdRepository = mock(HouseholdRepository.class);
        memberRepository = mock(HouseholdMemberRepository.class);
        shareRepository = mock(HouseholdAccountShareRepository.class);
        accountRepository = mock(AccountRepository.class);
        userRepository = mock(UserRepository.class);

        service = new ManageHouseholdImpl(
            householdRepository,
            memberRepository,
            shareRepository,
            accountRepository,
            userRepository,
            new OwnershipGuard()
        );

        when(memberRepository.findByUserUserIdAndStatus(1L, MemberStatus.INVITED)).thenReturn(List.of());
        when(memberRepository.findByUserUserIdAndStatus(2L, MemberStatus.INVITED)).thenReturn(List.of());
        when(memberRepository.findByUserUserIdAndStatus(3L, MemberStatus.INVITED)).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Household creation")
    class HouseholdCreation {

        @Test
        @DisplayName("creates household with creator as OWNER and ACTIVE")
        void createsHouseholdWithOwner() {
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> {
                Household h = invocation.getArgument(0);
                h.setId(UUID.randomUUID());
                return h;
            });

            HouseholdDto result = service.createHousehold(new CreateHouseholdDto("Family"), userA);

            assertEquals("Family", result.getName());
            assertEquals(1, result.getMembers().size());
            assertEquals(1L, result.getMembers().get(0).getUserId());
            assertEquals("OWNER", result.getMembers().get(0).getRole());
            assertEquals("ACTIVE", result.getMembers().get(0).getStatus());
        }

        @Test
        @DisplayName("trims whitespace from household name")
        void trimsHouseholdName() {
            when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> {
                Household h = invocation.getArgument(0);
                h.setId(UUID.randomUUID());
                return h;
            });

            HouseholdDto result = service.createHousehold(new CreateHouseholdDto("  Family  "), userA);

            assertEquals("Family", result.getName());
        }

        @Test
        @DisplayName("cannot create a new household when already active in another household")
        void cannotCreateWhenAlreadyActiveElsewhere() {
            when(memberRepository.existsByUserUserIdAndStatus(1L, MemberStatus.ACTIVE)).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createHousehold(new CreateHouseholdDto("Family"), userA)
            );

            assertEquals(409, ex.getStatusCode().value());
            assertTrue(requireNonNull(ex.getReason()).contains("already an active member"));
        }
    }

    @Nested
    @DisplayName("Account sharing visibility")
    class AccountSharingVisibility {

        @Test
        @DisplayName("shared accounts appear in household account list for all members")
        void sharedAccountAppearsForAllMembers() {
            stubActiveMember(userB, HouseholdRole.MEMBER);
            HouseholdAccountShare share = new HouseholdAccountShare(household, checkingAccount, userA, Instant.now());
            when(shareRepository.findByHouseholdIdWithAccountAndOwner(householdId)).thenReturn(List.of(share));

            List<AccountDto> accounts = service.listHouseholdAccounts(householdId, userB);

            assertEquals(1, accounts.size());
            assertEquals(checkingId, accounts.get(0).getId());
            assertEquals(1L, accounts.get(0).getOwnerUserId());
            assertEquals("Checking", accounts.get(0).getName());
            assertEquals("ASSET", accounts.get(0).getType());
            assertEquals("EUR", accounts.get(0).getCurrency());
        }

        @Test
        @DisplayName("unshared account disappears from household account list")
        void unsharingRemovesAccount() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            HouseholdAccountShare share = new HouseholdAccountShare(household, checkingAccount, userA, Instant.now());
            when(shareRepository.findByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(share);

            service.unshareAccount(householdId, checkingId, userA);

            verify(shareRepository).delete(share);
        }

        @Test
        @DisplayName("non-shared accounts remain invisible in household mode")
        void nonSharedAccountsInvisible() {
            stubActiveMember(userB, HouseholdRole.MEMBER);
            HouseholdAccountShare share = new HouseholdAccountShare(household, checkingAccount, userA, Instant.now());
            when(shareRepository.findByHouseholdIdWithAccountAndOwner(householdId)).thenReturn(List.of(share));

            List<AccountDto> accounts = service.listHouseholdAccounts(householdId, userB);

            assertEquals(1, accounts.size());
            assertEquals("Checking", accounts.get(0).getName());
            assertFalse(accounts.stream().anyMatch(account -> "Secret Savings".equals(account.getName())));
        }

        @Test
        @DisplayName("listSharedAccounts returns share metadata for active members")
        void listSharedAccountsReturnsMetadata() {
            stubActiveMember(userB, HouseholdRole.MEMBER);
            HouseholdAccountShare share = new HouseholdAccountShare(household, checkingAccount, userA, Instant.now());
            when(shareRepository.findByHouseholdIdWithAccountAndOwner(householdId)).thenReturn(List.of(share));

            List<HouseholdAccountShareDto> shares = service.listSharedAccounts(householdId, userB);

            assertEquals(1, shares.size());
            assertEquals(checkingId, shares.get(0).getAccountId());
            assertEquals("Checking", shares.get(0).getAccountName());
            assertEquals("alice@example.com", shares.get(0).getOwnerEmail());
        }
    }

    @Nested
    @DisplayName("Account sharing operations")
    class AccountSharingOperations {

        @Test
        @DisplayName("owner can share their account into a household")
        void ownerCanShareAccount() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checkingAccount));
            when(shareRepository.existsByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(false);
            when(shareRepository.save(any(HouseholdAccountShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdAccountShareDto result = service.shareAccount(householdId, new ShareAccountDto(checkingId), userA);

            assertEquals(checkingId, result.getAccountId());
            assertEquals("Checking", result.getAccountName());
            assertEquals("alice@example.com", result.getOwnerEmail());
        }

        @Test
        @DisplayName("non-owner cannot share someone else's account")
        void nonOwnerCannotShare() {
            stubActiveMember(userB, HouseholdRole.MEMBER);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checkingAccount));

            ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> service.shareAccount(householdId, new ShareAccountDto(checkingId), userB)
            );

            assertTrue(requireNonNull(ex.getMessage()).contains("owner"));
        }

        @Test
        @DisplayName("duplicate share throws CONFLICT")
        void duplicateShareThrowsConflict() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checkingAccount));
            when(shareRepository.existsByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.shareAccount(householdId, new ShareAccountDto(checkingId), userA)
            );

            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("non-owner cannot unshare someone else's account")
        void nonOwnerCannotUnshare() {
            stubActiveMember(userB, HouseholdRole.MEMBER);
            HouseholdAccountShare share = new HouseholdAccountShare(household, checkingAccount, userA, Instant.now());
            when(shareRepository.findByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(share);

            assertThrows(ForbiddenException.class, () -> service.unshareAccount(householdId, checkingId, userB));
        }

        @Test
        @DisplayName("unshare non-existent share throws NOT_FOUND")
        void unshareNonExistentThrows404() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(shareRepository.findByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(null);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.unshareAccount(householdId, checkingId, userA)
            );

            assertEquals(404, ex.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("Membership role enforcement")
    class MembershipRoleEnforcement {

        @Test
        @DisplayName("OWNER can invite new members")
        void ownerCanInviteMembers() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC));
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);
            when(memberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberDto result = service.inviteMember(householdId, new InviteMemberDto("carol@example.com"), userA);

            assertEquals(3L, result.getUserId());
            assertEquals("MEMBER", result.getRole());
            assertEquals("INVITED", result.getStatus());
        }

        @Test
        @DisplayName("ADMIN can invite new members")
        void adminCanInviteMembers() {
            stubActiveMember(userB, HouseholdRole.ADMIN);
            when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC));
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);
            when(memberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberDto result = service.inviteMember(householdId, new InviteMemberDto("carol@example.com"), userB);

            assertEquals(3L, result.getUserId());
            assertEquals("INVITED", result.getStatus());
        }

        @Test
        @DisplayName("MEMBER cannot invite new members")
        void memberCannotInviteMembers() {
            stubActiveMember(userB, HouseholdRole.MEMBER);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.inviteMember(householdId, new InviteMemberDto("carol@example.com"), userB)
            );

            assertEquals(403, ex.getStatusCode().value());
            assertTrue(requireNonNull(ex.getReason()).contains("OWNER or ADMIN"));
        }

        @Test
        @DisplayName("cannot invite a user who is active in another household")
        void cannotInviteUserActiveElsewhere() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC));
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);
            when(memberRepository.existsByUserUserIdAndStatus(3L, MemberStatus.ACTIVE)).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.inviteMember(householdId, new InviteMemberDto("carol@example.com"), userA)
            );

            assertEquals(409, ex.getStatusCode().value());
            assertTrue(requireNonNull(ex.getReason()).contains("active member"));
        }

        @Test
        @DisplayName("INVITED (non-ACTIVE) user cannot list household accounts")
        void invitedMemberCannotAccessHouseholdAccounts() {
            HouseholdMember invited = invitedMember(userC);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(invited);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.listHouseholdAccounts(householdId, userC)
            );

            assertEquals(403, ex.getStatusCode().value());
            assertTrue(requireNonNull(ex.getReason()).contains("not active"));
        }

        @Test
        @DisplayName("non-member cannot access household")
        void nonMemberCannotAccess() {
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.listHouseholdAccounts(householdId, userC)
            );

            assertEquals(403, ex.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("Invite acceptance")
    class InviteAcceptance {

        @Test
        @DisplayName("accepting invite changes status from INVITED to ACTIVE")
        void acceptInviteChangesStatusToActive() {
            HouseholdMember invited = invitedMember(userB);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(invited);
            when(memberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberDto result = service.acceptInvite(householdId, userB);

            assertEquals("ACTIVE", result.getStatus());
            assertNotNull(result.getJoinedAt());
        }

        @Test
        @DisplayName("accepting when already ACTIVE throws BAD_REQUEST")
        void acceptingWhenAlreadyActiveThrows() {
            HouseholdMember active = activeMember(userB, HouseholdRole.MEMBER);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(active);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.acceptInvite(householdId, userB)
            );

            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("accepting non-existent invite throws NOT_FOUND")
        void acceptingNonExistentInviteThrows() {
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(null);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.acceptInvite(householdId, userB)
            );

            assertEquals(404, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("cannot accept invite when user is already active in another household")
        void cannotAcceptInviteWhenAlreadyActiveElsewhere() {
            HouseholdMember invited = invitedMember(userB);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(invited);
            when(memberRepository.existsByUserUserIdAndStatusAndHouseholdIdNot(2L, MemberStatus.ACTIVE, householdId))
                .thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.acceptInvite(householdId, userB)
            );

            assertEquals(409, ex.getStatusCode().value());
            assertTrue(requireNonNull(ex.getReason()).contains("Leave your current household"));
        }
    }

    @Nested
    @DisplayName("Invite edge cases")
    class InviteEdgeCases {

        @Test
        @DisplayName("inviting already-existing member throws CONFLICT")
        void invitingExistingMemberThrowsConflict() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(userB));
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L))
                .thenReturn(activeMember(userB, HouseholdRole.MEMBER));

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.inviteMember(householdId, new InviteMemberDto("bob@example.com"), userA)
            );

            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("inviting non-existent user throws BAD_REQUEST")
        void invitingNonExistentUserThrows() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.inviteMember(householdId, new InviteMemberDto("nobody@example.com"), userA)
            );

            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("invite trims email whitespace")
        void inviteTrimsEmail() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(userC));
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);
            when(memberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            HouseholdMemberDto result = service.inviteMember(
                householdId,
                new InviteMemberDto("  carol@example.com  "),
                userA
            );

            assertEquals(3L, result.getUserId());
        }
    }

    @Nested
    @DisplayName("Household listing and detail")
    class HouseholdListingAndDetail {

        @Test
        @DisplayName("listHouseholds returns memberships with status")
        void listHouseholdsReturnsMembershipsWithStatus() {
            HouseholdMember member = activeMember(userA, HouseholdRole.OWNER);
            HouseholdMember invite = invitedMember(userA);
            when(memberRepository.findByUserUserId(1L)).thenReturn(List.of(member, invite));

            List<HouseholdSummaryDto> result = service.listHouseholds(userA);

            assertEquals(2, result.size());
            assertEquals("ACTIVE", result.get(0).getStatus());
            assertEquals("INVITED", result.get(1).getStatus());
        }

        @Test
        @DisplayName("getHousehold throws FORBIDDEN for non-member")
        void getHouseholdForbiddenForNonMember() {
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.getHousehold(householdId, userC)
            );

            assertEquals(403, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("getHousehold returns details for active member")
        void getHouseholdReturnsDetailsForActiveMember() {
            stubActiveMember(userA, HouseholdRole.OWNER);
            when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));

            HouseholdDto result = service.getHousehold(householdId, userA);

            assertEquals(householdId, result.getId());
            assertEquals("Test Household", result.getName());
        }
    }

    @Nested
    @DisplayName("isActiveMember and isAccountShared helpers")
    class HelperMethods {

        @Test
        @DisplayName("isActiveMember returns true for ACTIVE member")
        void isActiveMemberReturnsTrue() {
            HouseholdMember member = activeMember(userA, HouseholdRole.OWNER);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 1L)).thenReturn(member);

            assertTrue(service.isActiveMember(householdId, 1L));
        }

        @Test
        @DisplayName("isActiveMember returns false for INVITED member")
        void isActiveMemberReturnsFalseForInvited() {
            HouseholdMember invited = invitedMember(userB);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(invited);

            assertFalse(service.isActiveMember(householdId, 2L));
        }

        @Test
        @DisplayName("isActiveMember returns false for non-member")
        void isActiveMemberReturnsFalseForNonMember() {
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 3L)).thenReturn(null);

            assertFalse(service.isActiveMember(householdId, 3L));
        }

        @Test
        @DisplayName("isAccountShared delegates to repository")
        void isAccountSharedDelegatesToRepo() {
            when(shareRepository.existsByHouseholdIdAndAccountId(householdId, checkingId)).thenReturn(true);

            assertTrue(service.isAccountShared(householdId, checkingId));
        }

        @Test
        @DisplayName("getSharedAccountIds returns account IDs from repository")
        void getSharedAccountIdsReturnsIds() {
            Set<UUID> expectedIds = Set.of(checkingId, savingsId);
            when(shareRepository.findSharedAccountIdsByHouseholdId(householdId)).thenReturn(expectedIds);

            Set<UUID> result = service.getSharedAccountIds(householdId);

            assertEquals(expectedIds, result);
            verify(shareRepository).findSharedAccountIdsByHouseholdId(householdId);
        }

        @Test
        @DisplayName("getSharedAccountIds returns empty set when no accounts are shared")
        void getSharedAccountIdsReturnsEmptySet() {
            when(shareRepository.findSharedAccountIdsByHouseholdId(householdId)).thenReturn(Set.of());

            Set<UUID> result = service.getSharedAccountIds(householdId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Leave household")
    class LeaveHousehold {

        @Test
        @DisplayName("active member can leave household")
        void activeMemberCanLeave() {
            HouseholdMember member = activeMember(userB, HouseholdRole.MEMBER);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 2L)).thenReturn(member);
            when(memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE))
                .thenReturn(List.of(activeMember(userA, HouseholdRole.OWNER)));

            service.leaveHousehold(householdId, userB);

            verify(shareRepository).deleteByHouseholdIdAndAccountOwnerUserId(householdId, 2L);
            verify(memberRepository).delete(member);
        }

        @Test
        @DisplayName("owner leave promotes an active admin to owner")
        void ownerLeavePromotesAdmin() {
            HouseholdMember ownerMember = activeMember(userA, HouseholdRole.OWNER);
            HouseholdMember adminMember = activeMember(userB, HouseholdRole.ADMIN);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 1L)).thenReturn(ownerMember);
            when(memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE))
                .thenReturn(List.of(adminMember));
            when(memberRepository.save(any(HouseholdMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.leaveHousehold(householdId, userA);

            assertEquals(HouseholdRole.OWNER, adminMember.getRole());
            verify(memberRepository).save(adminMember);
        }

        @Test
        @DisplayName("last active member leaving deletes household")
        void lastActiveMemberLeavingDeletesHousehold() {
            HouseholdMember ownerMember = activeMember(userA, HouseholdRole.OWNER);
            when(memberRepository.findByHouseholdIdAndUserUserId(householdId, 1L)).thenReturn(ownerMember);
            when(memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE)).thenReturn(List.of());
            when(memberRepository.findByHouseholdId(householdId)).thenReturn(List.of());

            service.leaveHousehold(householdId, userA);

            verify(shareRepository).deleteByHouseholdId(householdId);
            verify(householdRepository).delete(household);
        }
    }

    private static User buildUser(long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded");
        user.setEmail(email);
        return user;
    }

    private HouseholdMember activeMember(User user, HouseholdRole role) {
        return new HouseholdMember(
            household,
            user,
            role,
            MemberStatus.ACTIVE,
            null,
            null,
            Instant.now()
        );
    }

    private HouseholdMember invitedMember(User user) {
        return new HouseholdMember(
            household,
            user,
            HouseholdRole.MEMBER,
            MemberStatus.INVITED,
            userA,
            Instant.now(),
            null
        );
    }

    private void stubActiveMember(User user, HouseholdRole role) {
        HouseholdMember member = activeMember(user, role);
        long userId = requireNonNull(user.getUserId());
        when(memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)).thenReturn(member);
    }
}
