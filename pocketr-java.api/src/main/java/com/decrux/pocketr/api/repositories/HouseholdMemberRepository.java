package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.household.HouseholdMember;
import com.decrux.pocketr.api.entities.db.household.HouseholdMemberId;
import com.decrux.pocketr.api.entities.db.household.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {

    List<HouseholdMember> findByUserUserId(long userId);

    List<HouseholdMember> findByUserUserIdAndStatus(long userId, MemberStatus status);

    boolean existsByUserUserIdAndStatus(long userId, MemberStatus status);

    boolean existsByUserUserIdAndStatusAndHouseholdIdNot(long userId, MemberStatus status, UUID householdId);

    HouseholdMember findByHouseholdIdAndUserUserId(UUID householdId, long userId);

    default Optional<HouseholdMember> findOptionalByHouseholdIdAndUserUserId(UUID householdId, long userId) {
        return Optional.ofNullable(findByHouseholdIdAndUserUserId(householdId, userId));
    }

    List<HouseholdMember> findByHouseholdIdAndStatus(UUID householdId, MemberStatus status);

    List<HouseholdMember> findByHouseholdId(UUID householdId);
}
