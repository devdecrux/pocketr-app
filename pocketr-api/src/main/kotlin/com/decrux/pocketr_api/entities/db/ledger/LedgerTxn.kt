package com.decrux.pocketr_api.entities.db.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.household.Household
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "ledger_txn",
    indexes = [
        Index(name = "idx_ledger_txn_date", columnList = "txn_date"),
        Index(name = "idx_ledger_txn_household", columnList = "household_id"),
        Index(name = "idx_ledger_txn_creator", columnList = "created_by_user_id"),
        Index(name = "idx_ledger_txn_household_date", columnList = "household_id, txn_date"),
    ],
)
class LedgerTxn(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User? = null,
    @Column(name = "household_id")
    var householdId: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "household_id",
        insertable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_ledger_txn_household"),
    )
    var household: Household? = null,
    @Column(name = "txn_date", nullable = false)
    var txnDate: LocalDate = LocalDate.now(),
    @Column(nullable = false)
    var description: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency", nullable = false)
    var currency: Currency? = null,
    @Column(name = "fx_group_id")
    var fxGroupId: UUID? = null,
    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    var splits: MutableList<LedgerSplit> = mutableListOf(),
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    fun onCreate() {
        updatedAt = Instant.now()
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
