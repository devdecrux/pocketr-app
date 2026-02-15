package com.decrux.pocketr_api.entities.db.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "ledger_txn")
class LedgerTxn(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User? = null,
    @Column(name = "household_id")
    var householdId: UUID? = null,
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
)
