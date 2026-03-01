package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.household.HouseholdMember
import com.decrux.pocketr.api.entities.db.household.HouseholdMemberId
import com.decrux.pocketr.api.entities.db.household.MemberStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface HouseholdMemberRepository : JpaRepository<HouseholdMember, HouseholdMemberId> {
    fun findByUserUserId(userId: Long): List<HouseholdMember>

    fun findByUserUserIdAndStatus(
        userId: Long,
        status: MemberStatus,
    ): List<HouseholdMember>

    fun existsByUserUserIdAndStatus(
        userId: Long,
        status: MemberStatus,
    ): Boolean

    fun existsByUserUserIdAndStatusAndHouseholdIdNot(
        userId: Long,
        status: MemberStatus,
        householdId: UUID,
    ): Boolean

    fun findByHouseholdIdAndUserUserId(
        householdId: UUID,
        userId: Long,
    ): HouseholdMember?

    fun findByHouseholdIdAndStatus(
        householdId: UUID,
        status: MemberStatus,
    ): List<HouseholdMember>

    fun findByHouseholdId(householdId: UUID): List<HouseholdMember>
}
