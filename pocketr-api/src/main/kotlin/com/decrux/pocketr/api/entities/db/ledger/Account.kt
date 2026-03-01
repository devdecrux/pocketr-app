package com.decrux.pocketr.api.entities.db.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "account",
    indexes = [Index(name = "idx_account_owner", columnList = "owner_user_id")],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_account_owner_type_currency_name",
            columnNames = ["owner_user_id", "type", "currency", "name"],
        ),
    ],
)
class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    var owner: User? = null,
    @Column(nullable = false)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AccountType = AccountType.ASSET,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency", nullable = false)
    var currency: Currency? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
