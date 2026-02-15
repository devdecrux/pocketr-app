package com.decrux.pocketr_api.services.household

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.household.*
import com.decrux.pocketr_api.entities.dtos.*
import com.decrux.pocketr_api.repositories.*
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
) : ManageHousehold {

    @Transactional
    override fun createHousehold(dto: CreateHouseholdDto, creator: User): HouseholdDto {
        val household = Household(
            name = dto.name.trim(),
            createdBy = creator,
        )

        val ownerMember = HouseholdMember(
            household = household,
            user = creator,
            role = HouseholdRole.OWNER,
            status = MemberStatus.ACTIVE,
            joinedAt = Instant.now(),
        )
        household.members.add(ownerMember)

        val saved = householdRepository.save(household)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    override fun listHouseholds(user: User): List<HouseholdSummaryDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        return memberRepository.findByUserUserIdAndStatus(userId, MemberStatus.ACTIVE)
            .map { member ->
                val household = requireNotNull(member.household)
                HouseholdSummaryDto(
                    id = requireNotNull(household.id),
                    name = household.name,
                    role = member.role.name,
                    createdAt = household.createdAt,
                )
            }
    }

    @Transactional(readOnly = true)
    override fun getHousehold(id: UUID, user: User): HouseholdDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(id, userId)

        val household = householdRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found") }

        return household.toDto()
    }

    @Transactional
    override fun inviteMember(householdId: UUID, dto: InviteMemberDto, inviter: User): HouseholdMemberDto {
        val inviterId = requireNotNull(inviter.userId) { "User ID must not be null" }
        val inviterMember = requireActiveMembership(householdId, inviterId)

        if (inviterMember.role !in setOf(HouseholdRole.OWNER, HouseholdRole.ADMIN)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER or ADMIN can invite members")
        }

        val household = requireNotNull(inviterMember.household)

        val invitee = userRepository.findByEmail(dto.email.trim())
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found with email: ${dto.email}") }

        val existingMember = memberRepository.findByHouseholdIdAndUserUserId(householdId, requireNotNull(invitee.userId))
        if (existingMember != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User is already a member or has a pending invite")
        }

        val member = HouseholdMember(
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
    override fun acceptInvite(householdId: UUID, user: User): HouseholdMemberDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        val member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No invite found for this household")

        if (member.status != MemberStatus.INVITED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite to accept")
        }

        member.status = MemberStatus.ACTIVE
        member.joinedAt = Instant.now()

        return memberRepository.save(member).toDto()
    }

    @Transactional
    override fun shareAccount(householdId: UUID, dto: ShareAccountDto, user: User): HouseholdAccountShareDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        val household = householdRepository.findById(householdId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found") }

        val account = accountRepository.findById(dto.accountId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found") }

        if (account.owner?.userId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the account owner can share an account")
        }

        if (shareRepository.existsByHouseholdIdAndAccountId(householdId, dto.accountId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Account is already shared into this household")
        }

        val share = HouseholdAccountShare(
            household = household,
            account = account,
            sharedBy = user,
        )

        return shareRepository.save(share).toDto()
    }

    @Transactional
    override fun unshareAccount(householdId: UUID, accountId: UUID, user: User) {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        val share = shareRepository.findByHouseholdIdAndAccountId(householdId, accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account is not shared into this household")

        if (share.account?.owner?.userId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the account owner can unshare an account")
        }

        shareRepository.delete(share)
    }

    @Transactional(readOnly = true)
    override fun listSharedAccounts(householdId: UUID, user: User): List<HouseholdAccountShareDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        return shareRepository.findByHouseholdIdWithAccountAndOwner(householdId).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun listHouseholdAccounts(householdId: UUID, user: User): List<AccountDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        requireActiveMembership(householdId, userId)

        return shareRepository.findByHouseholdIdWithAccountAndOwner(householdId).map { share ->
            val account = requireNotNull(share.account)
            AccountDto(
                id = requireNotNull(account.id),
                name = account.name,
                type = account.type.name,
                currency = requireNotNull(account.currency?.code),
                isArchived = account.isArchived,
                createdAt = account.createdAt,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun isActiveMember(householdId: UUID, userId: Long): Boolean {
        val member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
        return member != null && member.status == MemberStatus.ACTIVE
    }

    @Transactional(readOnly = true)
    override fun isAccountShared(householdId: UUID, accountId: UUID): Boolean {
        return shareRepository.existsByHouseholdIdAndAccountId(householdId, accountId)
    }

    private fun requireActiveMembership(householdId: UUID, userId: Long): HouseholdMember {
        val member = memberRepository.findByHouseholdIdAndUserUserId(householdId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this household")
        if (member.status != MemberStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Membership is not active")
        }
        return member
    }

    private companion object {
        fun Household.toDto() = HouseholdDto(
            id = requireNotNull(id) { "Household ID must not be null" },
            name = name,
            createdAt = createdAt,
            members = members.map { it.toDto() },
        )

        fun HouseholdMember.toDto() = HouseholdMemberDto(
            userId = requireNotNull(user?.userId) { "User ID must not be null" },
            username = requireNotNull(user?.username) { "Username must not be null" },
            role = role.name,
            status = status.name,
            joinedAt = joinedAt,
        )

        fun HouseholdAccountShare.toDto() = HouseholdAccountShareDto(
            accountId = requireNotNull(account?.id) { "Account ID must not be null" },
            accountName = requireNotNull(account?.name) { "Account name must not be null" },
            ownerUsername = requireNotNull(account?.owner?.username) { "Owner username must not be null" },
            sharedAt = sharedAt,
        )
    }
}
