package com.decrux.pocketr.api.entities.db.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "account",
    indexes = {
        @Index(name = "idx_account_owner", columnList = "owner_user_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_account_owner_type_currency_name",
            columnNames = {"owner_user_id", "type", "currency", "name"}
        )
    }
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type = AccountType.ASSET;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Account() {
    }

    public Account(UUID id, User owner, String name, AccountType type, Currency currency, Instant createdAt) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
