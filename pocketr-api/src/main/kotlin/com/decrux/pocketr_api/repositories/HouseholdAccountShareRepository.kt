package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.household.HouseholdAccountShare
import com.decrux.pocketr_api.entities.db.household.HouseholdAccountShareId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface HouseholdAccountShareRepository : JpaRepository<HouseholdAccountShare, HouseholdAccountShareId> {

    fun findByHouseholdId(householdId: UUID): List<HouseholdAccountShare>

    @Query("SELECT s FROM HouseholdAccountShare s JOIN FETCH s.account a JOIN FETCH a.owner WHERE s.household.id = :householdId")
    fun findByHouseholdIdWithAccountAndOwner(householdId: UUID): List<HouseholdAccountShare>

    fun findByHouseholdIdAndAccountId(householdId: UUID, accountId: UUID): HouseholdAccountShare?

    fun existsByHouseholdIdAndAccountId(householdId: UUID, accountId: UUID): Boolean
}
