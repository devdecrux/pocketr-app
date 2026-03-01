package com.decrux.pocketr_api.services.household

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.household.Household
import com.decrux.pocketr_api.entities.db.household.HouseholdAccountShare
import com.decrux.pocketr_api.entities.db.household.HouseholdMember
import com.decrux.pocketr_api.entities.db.household.HouseholdRole
import com.decrux.pocketr_api.entities.db.household.MemberStatus
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateHouseholdDto
import com.decrux.pocketr_api.entities.dtos.HouseholdAccountShareDto
import com.decrux.pocketr_api.entities.dtos.HouseholdDto
import com.decrux.pocketr_api.entities.dtos.HouseholdMemberDto
import com.decrux.pocketr_api.entities.dtos.HouseholdSummaryDto
import com.decrux.pocketr_api.entities.dtos.InviteMemberDto
import com.decrux.pocketr_api.entities.dtos.ShareAccountDto
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.HouseholdAccountShareRepository
import com.decrux.pocketr_api.repositories.HouseholdMemberRepository
import com.decrux.pocketr_api.repositories.HouseholdRepository
import com.decrux.pocketr_api.repositories.UserRepository
import com.decrux.pocketr_api.services.OwnershipGuard
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class ManageHouseholdImpl(
    private val householdRepository: HouseholdRepository,
    private val memberRepository: HouseholdMemberRepository,
    private val shareRepository: HouseholdAccountShareRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val ownershipGuard: OwnershipGuard,
) : ManageHousehold {
    @Transactional
    override fun createHousehold(
        dto: CreateHouseholdDto,
        creator: User,
    ): HouseholdDto {
        val creatorId = requireNotNull(creator.userId) { "User ID must not be null" }
        ensureNoActiveMembership(
            creatorId,
            "Cannot create a household while already an active member of another household",
        )

        val household =
            Household(
                name = dto.name.trim(),
                createdBy = creator,
            )

        val ownerMember =
            HouseholdMember(
                household = household,
                user = creator,
                role = HouseholdRole.OWNER,
                status = MemberStatus.ACTIVE,
                joinedAt = Instant.now(),
            )
        household.members.add(ownerMember)

        val saved = householdRepository.save(household)
        deletePendingInvitesExcept(creatorId, requireNotNull(saved.id) { "Household ID must not be null" })
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    override fun listHouseholds(user: User): List<HouseholdSummaryDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        return memberRepository
            .findByUserUserId(userId)
            .sortedWith(
                compareByDescending<HouseholdMember> { it.status == MemberStatus.ACTIVE }
                    .thenByDescending { requireNotNull(it.household).createdAt },
            ).map { member ->
                val household = requireNotNull(member.household)
                HouseholdSummaryDto(
                    id = requireNotNull(household.id),
                    name = household.name,
                    role = member.role.name,
                    status = member.status.name,
                    createdAt = household.createdAt,
                )
            }
    }

    @Transactional(readOnly = true)
    override fun getHousehold(
        id: UUID,
        user: User,
    ): HouseholdDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(id, userId)

        val household =
            householdRepository
                .findById(id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found") }

        return household.toDto()
    }

    @Transactional
    override fun inviteMember(
        householdId: UUID,
        dto: InviteMemberDto,
        inviter: User,
    ): HouseholdMemberDto {
        val inviterId = requireNotNull(inviter.userId) { "User ID must not be null" }
        val inviterMember = requireActiveMembership(householdId, inviterId)

        if (inviterMember.role !in setOf(HouseholdRole.OWNER, HouseholdRole.ADMIN)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER or ADMIN can invite members")
        }

        val household = requireNotNull(inviterMember.household)

        val invitee =
            userRepository
                .findByEmail(dto.email.trim())
                .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found with email: ${dto.email}") }
        val inviteeId = requireNotNull(invitee.userId) { "User ID must not be null" }

        val existingMember = memberRepository.findByHouseholdIdAndUserUserId(householdId, inviteeId)
        if (existingMember != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User is already a member or has a pending invite")
        }
        ensureNoActiveMembership(
            inviteeId,
            "User is already an active member of another household",
        )

        val member =
            HouseholdMember(
                household = household,
                user = invitee,
                role = HouseholdRole.MEMBER,
                status = MemberStatus.INVITED,
                invitedBy = inviter,
                invitedAt = Instant.now(),
            )

        return memberRepository.save(member).toDto()
    }

    @Transactional
    override fun acceptInvite(
        householdId: UUID,
        user: User,
    ): HouseholdMemberDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        val member =
            memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No invite found for this household")

        if (member.status != MemberStatus.INVITED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite to accept")
        }
        if (memberRepository.existsByUserUserIdAndStatusAndHouseholdIdNot(userId, MemberStatus.ACTIVE, householdId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Leave your current household before accepting another invite",
            )
        }

        member.status = MemberStatus.ACTIVE
        member.joinedAt = Instant.now()

        val saved = memberRepository.save(member)
        deletePendingInvitesExcept(userId, householdId)
        return saved.toDto()
    }

    @Transactional
    override fun leaveHousehold(
        householdId: UUID,
        user: User,
    ) {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        val member =
            memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Not a member of this household")

        if (member.status != MemberStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only active members can leave a household")
        }

        val household = requireNotNull(member.household) { "Household must not be null" }

        // If the user leaves, their accounts should no longer be shared in this household.
        shareRepository.deleteByHouseholdIdAndAccountOwnerUserId(householdId, userId)
        memberRepository.delete(member)

        val remainingActiveMembers = memberRepository.findByHouseholdIdAndStatus(householdId, MemberStatus.ACTIVE)
        if (remainingActiveMembers.isEmpty()) {
            shareRepository.deleteByHouseholdId(householdId)
            val remainingMembers = memberRepository.findByHouseholdId(householdId)
            if (remainingMembers.isNotEmpty()) {
                memberRepository.deleteAll(remainingMembers)
            }
            householdRepository.delete(household)
            return
        }

        if (member.role == HouseholdRole.OWNER) {
            val promotedOwner =
                remainingActiveMembers.firstOrNull { it.role == HouseholdRole.ADMIN }
                    ?: remainingActiveMembers.first()
            if (promotedOwner.role != HouseholdRole.OWNER) {
                promotedOwner.role = HouseholdRole.OWNER
                memberRepository.save(promotedOwner)
            }
        }
    }

    @Transactional
    override fun shareAccount(
        householdId: UUID,
        dto: ShareAccountDto,
        user: User,
    ): HouseholdAccountShareDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        val household =
            householdRepository
                .findById(householdId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found") }

        val account =
            accountRepository
                .findById(dto.accountId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found") }

        ownershipGuard.requireOwner(account.owner?.userId, userId, "Only the account owner can share an account")

        if (shareRepository.existsByHouseholdIdAndAccountId(householdId, dto.accountId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Account is already shared into this household")
        }

        val share =
            HouseholdAccountShare(
                household = household,
                account = account,
                sharedBy = user,
            )

        return shareRepository.save(share).toDto()
    }

    @Transactional
    override fun unshareAccount(
        householdId: UUID,
        accountId: UUID,
        user: User,
    ) {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        val share =
            shareRepository.findByHouseholdIdAndAccountId(householdId, accountId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account is not shared into this household")

        ownershipGuard.requireOwner(share.account?.owner?.userId, userId, "Only the account owner can unshare an account")

        shareRepository.delete(share)
    }

    @Transactional(readOnly = true)
    override fun listSharedAccounts(
        householdId: UUID,
        user: User,
    ): List<HouseholdAccountShareDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        return shareRepository.findByHouseholdIdWithAccountAndOwner(householdId).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun listHouseholdAccounts(
        householdId: UUID,
        user: User,
    ): List<AccountDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        return shareRepository.findByHouseholdIdWithAccountAndOwner(householdId).map { share ->
            val account = requireNotNull(share.account)
            AccountDto(
                id = requireNotNull(account.id),
                ownerUserId = requireNotNull(account.owner?.userId) { "Owner user ID must not be null" },
                name = account.name,
                type = account.type.name,
                currency = requireNotNull(account.currency?.code),
                createdAt = account.createdAt,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun isActiveMember(
        householdId: UUID,
        userId: Long,
    ): Boolean {
        val member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
        return member != null && member.status == MemberStatus.ACTIVE
    }

    @Transactional(readOnly = true)
    override fun isAccountShared(
        householdId: UUID,
        accountId: UUID,
    ): Boolean = shareRepository.existsByHouseholdIdAndAccountId(householdId, accountId)

    @Transactional(readOnly = true)
    override fun getSharedAccountIds(householdId: UUID): Set<UUID> = shareRepository.findSharedAccountIdsByHouseholdId(householdId)

    private fun requireActiveMembership(
        householdId: UUID,
        userId: Long,
    ): HouseholdMember {
        val member =
            memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this household")
        if (member.status != MemberStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Membership is not active")
        }
        return member
    }

    private fun ensureNoActiveMembership(
        userId: Long,
        reason: String,
    ) {
        if (memberRepository.existsByUserUserIdAndStatus(userId, MemberStatus.ACTIVE)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, reason)
        }
    }

    private fun deletePendingInvitesExcept(
        userId: Long,
        acceptedHouseholdId: UUID,
    ) {
        val staleInvites =
            memberRepository
                .findByUserUserIdAndStatus(userId, MemberStatus.INVITED)
                .filter { invite -> invite.household?.id != acceptedHouseholdId }
        if (staleInvites.isNotEmpty()) {
            memberRepository.deleteAll(staleInvites)
        }
    }

    private companion object {
        fun Household.toDto() =
            HouseholdDto(
                id = requireNotNull(id) { "Household ID must not be null" },
                name = name,
                createdAt = createdAt,
                members = members.map { it.toDto() },
            )

        fun HouseholdMember.toDto() =
            HouseholdMemberDto(
                userId = requireNotNull(user?.userId) { "User ID must not be null" },
                email = requireNotNull(user?.email) { "User email must not be null" },
                firstName = user?.firstName,
                lastName = user?.lastName,
                role = role.name,
                status = status.name,
                joinedAt = joinedAt,
            )

        fun HouseholdAccountShare.toDto() =
            HouseholdAccountShareDto(
                accountId = requireNotNull(account?.id) { "Account ID must not be null" },
                accountName = requireNotNull(account?.name) { "Account name must not be null" },
                ownerEmail = requireNotNull(account?.owner?.email) { "Owner email must not be null" },
                ownerFirstName = account?.owner?.firstName,
                ownerLastName = account?.owner?.lastName,
                sharedAt = sharedAt,
            )
    }
}
