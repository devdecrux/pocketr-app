package com.decrux.pocketr.api.entities.db.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.household.Household;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "ledger_txn",
    indexes = {
        @Index(name = "idx_ledger_txn_date", columnList = "txn_date"),
        @Index(name = "idx_ledger_txn_household", columnList = "household_id"),
        @Index(name = "idx_ledger_txn_creator", columnList = "created_by_user_id"),
        @Index(name = "idx_ledger_txn_household_date", columnList = "household_id, txn_date")
    }
)
public class LedgerTxn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "household_id")
    private UUID householdId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "household_id",
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_ledger_txn_household")
    )
    private Household household;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate = LocalDate.now();

    @Column(nullable = false)
    private String description = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency", nullable = false)
    private Currency currency;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerSplit> splits = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public LedgerTxn() {
    }

    public LedgerTxn(
        UUID id,
        User createdBy,
        UUID householdId,
        Household household,
        LocalDate txnDate,
        String description,
        Currency currency,
        List<LedgerSplit> splits,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.createdBy = createdBy;
        this.householdId = householdId;
        this.household = household;
        this.txnDate = txnDate;
        this.description = description;
        this.currency = currency;
        this.splits = splits;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(UUID householdId) {
        this.householdId = householdId;
    }

    public Household getHousehold() {
        return household;
    }

    public void setHousehold(Household household) {
        this.household = household;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public List<LedgerSplit> getSplits() {
        return splits;
    }

    public void setSplits(List<LedgerSplit> splits) {
        this.splits = splits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
