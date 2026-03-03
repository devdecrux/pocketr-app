package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShare;
import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShareId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface HouseholdAccountShareRepository extends JpaRepository<HouseholdAccountShare, HouseholdAccountShareId> {

    List<HouseholdAccountShare> findByHouseholdId(UUID householdId);

    void deleteByHouseholdId(UUID householdId);

    void deleteByHouseholdIdAndAccountOwnerUserId(UUID householdId, long ownerUserId);

    @Query(
        "SELECT s FROM HouseholdAccountShare s JOIN FETCH s.account a JOIN FETCH a.owner JOIN FETCH a.currency "
            + "WHERE s.household.id = :householdId"
    )
    List<HouseholdAccountShare> findByHouseholdIdWithAccountAndOwner(@Param("householdId") UUID householdId);

    HouseholdAccountShare findByHouseholdIdAndAccountId(UUID householdId, UUID accountId);

    default Optional<HouseholdAccountShare> findOptionalByHouseholdIdAndAccountId(UUID householdId, UUID accountId) {
        return Optional.ofNullable(findByHouseholdIdAndAccountId(householdId, accountId));
    }

    boolean existsByHouseholdIdAndAccountId(UUID householdId, UUID accountId);

    @Query("SELECT has.account.id FROM HouseholdAccountShare has WHERE has.household.id = :householdId")
    Set<UUID> findSharedAccountIdsByHouseholdId(@Param("householdId") UUID householdId);
}
