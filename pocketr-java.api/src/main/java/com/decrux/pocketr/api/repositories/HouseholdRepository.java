package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.household.Household;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
}
