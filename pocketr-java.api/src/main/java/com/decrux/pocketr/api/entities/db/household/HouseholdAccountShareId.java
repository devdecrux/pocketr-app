package com.decrux.pocketr.api.entities.db.household;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class HouseholdAccountShareId implements Serializable {

    private UUID household;
    private UUID account;

    public HouseholdAccountShareId() {
    }

    public HouseholdAccountShareId(UUID household, UUID account) {
        this.household = household;
        this.account = account;
    }

    public UUID getHousehold() {
        return household;
    }

    public void setHousehold(UUID household) {
        this.household = household;
    }

    public UUID getAccount() {
        return account;
    }

    public void setAccount(UUID account) {
        this.account = account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HouseholdAccountShareId that)) {
            return false;
        }
        return Objects.equals(household, that.household) && Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(household, account);
    }
}
