package com.decrux.pocketr.api.entities.db.ledger

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "account_current_balance",
    indexes = [Index(name = "idx_account_current_balance_updated_at", columnList = "updated_at")],
)
class AccountCurrentBalance(
    @Id
    @Column(name = "account_id", nullable = false)
    var accountId: UUID? = null,
    @Column(name = "raw_balance_minor", nullable = false)
    var rawBalanceMinor: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}
