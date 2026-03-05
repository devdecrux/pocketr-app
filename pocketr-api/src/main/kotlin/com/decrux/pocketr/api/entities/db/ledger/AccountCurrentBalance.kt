package com.decrux.pocketr.api.entities.db.ledger

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
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
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(
        name = "account_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_account_current_balance_account"),
    )
    var account: Account? = null,
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
