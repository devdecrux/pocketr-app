package com.decrux.pocketr.api.services.household;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.household.Household;
import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShare;
import com.decrux.pocketr.api.entities.db.household.HouseholdMember;
import com.decrux.pocketr.api.entities.db.household.HouseholdRole;
import com.decrux.pocketr.api.entities.db.household.MemberStatus;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateHouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdAccountShareDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdMemberDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdSummaryDto;
import com.decrux.pocketr.api.entities.dtos.InviteMemberDto;
import com.decrux.pocketr.api.entities.dtos.ShareAccountDto;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.HouseholdAccountShareRepository;
import com.decrux.pocketr.api.repositories.HouseholdMemberRepository;
import com.decrux.pocketr.api.repositories.HouseholdRepository;
import com.decrux.pocketr.api.repositories.UserRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManageHouseholdImpl implements ManageHousehold {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final HouseholdAccountShareRepository shareRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final OwnershipGuard ownershipGuard;

    public ManageHouseholdImpl(
        HouseholdRepository householdRepository,
        HouseholdMemberRepository memberRepository,
        HouseholdAccountShareRepository shareRepository,
        AccountRepository accountRepository,
        UserRepository userRepository,
        OwnershipGuard ownershipGuard
    ) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.shareRepository = shareRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    @Transactional
    public HouseholdDto createHousehold(CreateHouseholdDto dto, User creator) {
        long creatorId = requireNotNull(creator.getUserId(), "User ID must not be null");
        ensureNoActiveMembership(
            creatorId,
            "Cannot create a household while already an active member of another household"
        );

        Household household = new Household();
        household.setName(dto.getName().trim());
        household.setCreatedBy(creator);

        HouseholdMember ownerMember = new HouseholdMember();
        ownerMember.setHousehold(household);
        ownerMember.setUser(creator);
        ownerMember.setRole(HouseholdRole.OWNER);
        ownerMember.setStatus(MemberStatus.ACTIVE);
        ownerMember.setJoinedAt(Instant.now());

        List<HouseholdMember> members = household.getMembers();
        if (members == null) {
            members = new ArrayList<>();
            household.setMembers(members);
        }
        members.add(ownerMember);

        Household saved = householdRepository.save(household);
        deletePendingInvitesExcept(creatorId, requireNotNull(saved.getId(), "Household ID must not be null"));
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HouseholdSummaryDto> listHouseholds(User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        Comparator<HouseholdMember> comparator = Comparator
            .comparing((HouseholdMember member) -> member.getStatus() == MemberStatus.ACTIVE)
            .reversed()
            .thenComparing(
                member -> requireNotNull(requireNotNull(member.getHousehold(), "Household must not be null").getCreatedAt(), "Household createdAt must not be null"),
                Comparator.reverseOrder()
            );

        return memberRepository
            .findByUserUserId(userId)
            .stream()
            .sorted(comparator)
            .map(member -> {
                Household household = requireNotNull(member.getHousehold(), "Household must not be null");
                return new HouseholdSummaryDto(
                    requireNotNull(household.getId(), "Household ID must not be null"),
                    household.getName(),
                    member.getRole().name(),
                    member.getStatus().name(),
                    household.getCreatedAt()
                );
            })
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HouseholdDto getHousehold(UUID id, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        requireActiveMembership(id, userId);

        Household household = householdRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found"));

        return toDto(household);
    }

    @Override
    @Transactional
    public HouseholdMemberDto inviteMember(UUID householdId, InviteMemberDto dto, User inviter) {
        long inviterId = requireNotNull(inviter.getUserId(), "User ID must not be null");
        HouseholdMember inviterMember = requireActiveMembership(householdId, inviterId);

        if (inviterMember.getRole() != HouseholdRole.OWNER && inviterMember.getRole() != HouseholdRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER or ADMIN can invite members");
        }

        Household household = requireNotNull(inviterMember.getHousehold(), "Household must not be null");

        User invitee = userRepository
            .findByEmail(dto.getEmail().trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found with email: " + dto.getEmail()));
        long inviteeId = requireNotNull(invitee.getUserId(), "User ID must not be null");

        HouseholdMember existingMember = memberRepository.findByHouseholdIdAndUserUserId(householdId, inviteeId);
        if (existingMember != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member or has a pending invite");
        }

        ensureNoActiveMembership(inviteeId, "User is already an active member of another household");

        HouseholdMember member = new HouseholdMember();
        member.setHousehold(household);
        member.setUser(invitee);
        member.setRole(HouseholdRole.MEMBER);
        member.setStatus(MemberStatus.INVITED);
        member.setInvitedBy(inviter);
        member.setInvitedAt(Instant.now());

        return toDto(memberRepository.save(member));
    }

    @Override
    @Transactional
    public HouseholdMemberDto acceptInvite(UUID householdId, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");

        HouseholdMember member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No invite found for this household");
        }

        if (member.getStatus() != MemberStatus.INVITED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite to accept");
        }
        if (memberRepository.existsByUserUserIdAndStatusAndHouseholdIdNot(userId, MemberStatus.ACTIVE, householdId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Leave your current household before accepting another invite"
            );
        }

        member.setStatus(MemberStatus.ACTIVE);
        member.setJoinedAt(Instant.now());

        HouseholdMember saved = memberRepository.save(member);
        deletePendingInvitesExcept(userId, householdId);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void leaveHousehold(UUID householdId, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        HouseholdMember member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a member of this household");
        }

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only active members can leave a household");
        }

        Household household = requireNotNull(member.getHousehold(), "Household must not be null");

        shareRepository.deleteByHouseholdIdAndAccountOwnerUserId(householdId, userId);
        memberRepository.delete(member);

        List<HouseholdMember> remainingActiveMembers = memberRepository.findByHouseholdIdAndStatus(
            householdId,
            MemberStatus.ACTIVE
        );
        if (remainingActiveMembers.isEmpty()) {
            shareRepository.deleteByHouseholdId(householdId);
            List<HouseholdMember> remainingMembers = memberRepository.findByHouseholdId(householdId);
            if (!remainingMembers.isEmpty()) {
                memberRepository.deleteAll(remainingMembers);
            }
            householdRepository.delete(household);
            return;
        }

        if (member.getRole() == HouseholdRole.OWNER) {
            HouseholdMember promotedOwner = remainingActiveMembers
                .stream()
                .filter(activeMember -> activeMember.getRole() == HouseholdRole.ADMIN)
                .findFirst()
                .orElseGet(() -> remainingActiveMembers.getFirst());
            if (promotedOwner.getRole() != HouseholdRole.OWNER) {
                promotedOwner.setRole(HouseholdRole.OWNER);
                memberRepository.save(promotedOwner);
            }
        }
    }

    @Override
    @Transactional
    public HouseholdAccountShareDto shareAccount(UUID householdId, ShareAccountDto dto, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        requireActiveMembership(householdId, userId);

        Household household = householdRepository
            .findById(householdId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found"));

        var account = accountRepository
            .findById(dto.getAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        ownershipGuard.requireOwner(
            account.getOwner() != null ? account.getOwner().getUserId() : null,
            userId,
            "Only the account owner can share an account"
        );

        if (shareRepository.existsByHouseholdIdAndAccountId(householdId, dto.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already shared into this household");
        }

        HouseholdAccountShare share = new HouseholdAccountShare();
        share.setHousehold(household);
        share.setAccount(account);
        share.setSharedBy(user);

        return toDto(shareRepository.save(share));
    }

    @Override
    @Transactional
    public void unshareAccount(UUID householdId, UUID accountId, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        requireActiveMembership(householdId, userId);

        HouseholdAccountShare share = shareRepository.findByHouseholdIdAndAccountId(householdId, accountId);
        if (share == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account is not shared into this household");
        }

        ownershipGuard.requireOwner(
            share.getAccount() != null && share.getAccount().getOwner() != null
                ? share.getAccount().getOwner().getUserId()
                : null,
            userId,
            "Only the account owner can unshare an account"
        );

        shareRepository.delete(share);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HouseholdAccountShareDto> listSharedAccounts(UUID householdId, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        requireActiveMembership(householdId, userId);

        return shareRepository
            .findByHouseholdIdWithAccountAndOwner(householdId)
            .stream()
            .map(ManageHouseholdImpl::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> listHouseholdAccounts(UUID householdId, User user) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        requireActiveMembership(householdId, userId);

        return shareRepository.findByHouseholdIdWithAccountAndOwner(householdId).stream().map(share -> {
            var account = requireNotNull(share.getAccount(), "Account must not be null");
            return new AccountDto(
                requireNotNull(account.getId(), "Account ID must not be null"),
                requireNotNull(
                    account.getOwner() != null ? account.getOwner().getUserId() : null,
                    "Owner user ID must not be null"
                ),
                account.getName(),
                account.getType().name(),
                requireNotNull(account.getCurrency() != null ? account.getCurrency().getCode() : null, "Currency must not be null"),
                account.getCreatedAt()
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID householdId, long userId) {
        HouseholdMember member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId);
        return member != null && member.getStatus() == MemberStatus.ACTIVE;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAccountShared(UUID householdId, UUID accountId) {
        return shareRepository.existsByHouseholdIdAndAccountId(householdId, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getSharedAccountIds(UUID householdId) {
        return shareRepository.findSharedAccountIdsByHouseholdId(householdId);
    }

    private HouseholdMember requireActiveMembership(UUID householdId, long userId) {
        HouseholdMember member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this household");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membership is not active");
        }
        return member;
    }

    private void ensureNoActiveMembership(long userId, String reason) {
        if (memberRepository.existsByUserUserIdAndStatus(userId, MemberStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
        }
    }

    private void deletePendingInvitesExcept(long userId, UUID acceptedHouseholdId) {
        List<HouseholdMember> staleInvites = memberRepository
            .findByUserUserIdAndStatus(userId, MemberStatus.INVITED)
            .stream()
            .filter(invite -> !Objects.equals(
                invite.getHousehold() != null ? invite.getHousehold().getId() : null,
                acceptedHouseholdId
            ))
            .toList();
        if (!staleInvites.isEmpty()) {
            memberRepository.deleteAll(staleInvites);
        }
    }

    private static HouseholdDto toDto(Household household) {
        List<HouseholdMember> members = household.getMembers() != null ? household.getMembers() : List.of();
        return new HouseholdDto(
            requireNotNull(household.getId(), "Household ID must not be null"),
            household.getName(),
            household.getCreatedAt(),
            members.stream().map(ManageHouseholdImpl::toDto).toList()
        );
    }

    private static HouseholdMemberDto toDto(HouseholdMember member) {
        User user = member.getUser();
        return new HouseholdMemberDto(
            requireNotNull(user != null ? user.getUserId() : null, "User ID must not be null"),
            requireNotNull(user != null ? user.getEmail() : null, "User email must not be null"),
            user != null ? user.getFirstName() : null,
            user != null ? user.getLastName() : null,
            member.getRole().name(),
            member.getStatus().name(),
            member.getJoinedAt()
        );
    }

    private static HouseholdAccountShareDto toDto(HouseholdAccountShare share) {
        var account = share.getAccount();
        var owner = account != null ? account.getOwner() : null;
        return new HouseholdAccountShareDto(
            requireNotNull(account != null ? account.getId() : null, "Account ID must not be null"),
            requireNotNull(account != null ? account.getName() : null, "Account name must not be null"),
            requireNotNull(owner != null ? owner.getEmail() : null, "Owner email must not be null"),
            owner != null ? owner.getFirstName() : null,
            owner != null ? owner.getLastName() : null,
            share.getSharedAt()
        );
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
