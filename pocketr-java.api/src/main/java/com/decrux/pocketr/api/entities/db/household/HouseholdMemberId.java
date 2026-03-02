package com.decrux.pocketr.api.entities.db.household;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class HouseholdMemberId implements Serializable {

    private UUID household;
    private Long user;

    public HouseholdMemberId() {
    }

    public HouseholdMemberId(UUID household, Long user) {
        this.household = household;
        this.user = user;
    }

    public UUID getHousehold() {
        return household;
    }

    public void setHousehold(UUID household) {
        this.household = household;
    }

    public Long getUser() {
        return user;
    }

    public void setUser(Long user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HouseholdMemberId that)) {
            return false;
        }
        return Objects.equals(household, that.household) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(household, user);
    }
}
