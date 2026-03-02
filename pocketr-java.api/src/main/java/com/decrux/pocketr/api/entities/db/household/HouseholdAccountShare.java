package com.decrux.pocketr.api.entities.db.household;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "household_account_share")
@IdClass(HouseholdAccountShareId.class)
public class HouseholdAccountShare {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by", nullable = false)
    private User sharedBy;

    @Column(name = "shared_at", nullable = false, updatable = false)
    private Instant sharedAt = Instant.now();

    public HouseholdAccountShare() {
    }

    public HouseholdAccountShare(Household household, Account account, User sharedBy, Instant sharedAt) {
        this.household = household;
        this.account = account;
        this.sharedBy = sharedBy;
        this.sharedAt = sharedAt;
    }

    public Household getHousehold() {
        return household;
    }

    public void setHousehold(Household household) {
        this.household = household;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public User getSharedBy() {
        return sharedBy;
    }

    public void setSharedBy(User sharedBy) {
        this.sharedBy = sharedBy;
    }

    public Instant getSharedAt() {
        return sharedAt;
    }

    public void setSharedAt(Instant sharedAt) {
        this.sharedAt = sharedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HouseholdAccountShare that)) {
            return false;
        }
        return Objects.equals(getHouseholdId(), that.getHouseholdId())
            && Objects.equals(getAccountId(), that.getAccountId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHouseholdId(), getAccountId());
    }

    private UUID getHouseholdId() {
        return household != null ? household.getId() : null;
    }

    private UUID getAccountId() {
        return account != null ? account.getId() : null;
    }
}
