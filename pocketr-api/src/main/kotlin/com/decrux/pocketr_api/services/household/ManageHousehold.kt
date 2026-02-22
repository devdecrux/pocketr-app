package com.decrux.pocketr_api.services.household

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.*
import java.util.UUID

interface ManageHousehold {

    fun createHousehold(dto: CreateHouseholdDto, creator: User): HouseholdDto

    fun listHouseholds(user: User): List<HouseholdSummaryDto>

    fun getHousehold(id: UUID, user: User): HouseholdDto

    fun inviteMember(householdId: UUID, dto: InviteMemberDto, inviter: User): HouseholdMemberDto

    fun acceptInvite(householdId: UUID, user: User): HouseholdMemberDto

    fun leaveHousehold(householdId: UUID, user: User)

    fun shareAccount(householdId: UUID, dto: ShareAccountDto, user: User): HouseholdAccountShareDto

    fun unshareAccount(householdId: UUID, accountId: UUID, user: User)

    fun listSharedAccounts(householdId: UUID, user: User): List<HouseholdAccountShareDto>

    fun listHouseholdAccounts(householdId: UUID, user: User): List<AccountDto>

    fun isActiveMember(householdId: UUID, userId: Long): Boolean

    fun isAccountShared(householdId: UUID, accountId: UUID): Boolean

    fun getSharedAccountIds(householdId: UUID): Set<UUID>
}
