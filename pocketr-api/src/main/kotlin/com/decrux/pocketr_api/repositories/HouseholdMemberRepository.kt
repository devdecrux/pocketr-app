package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.household.HouseholdMember
import com.decrux.pocketr_api.entities.db.household.HouseholdMemberId
import com.decrux.pocketr_api.entities.db.household.MemberStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface HouseholdMemberRepository : JpaRepository<HouseholdMember, HouseholdMemberId> {

    fun findByUserUserIdAndStatus(userId: Long, status: MemberStatus): List<HouseholdMember>

    fun findByHouseholdIdAndUserUserId(householdId: UUID, userId: Long): HouseholdMember?

    fun findByHouseholdId(householdId: UUID): List<HouseholdMember>
}
