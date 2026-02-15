package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.household.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface HouseholdRepository : JpaRepository<Household, UUID>
