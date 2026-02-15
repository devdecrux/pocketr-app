package com.decrux.pocketr_api.entities.db.ledger

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "ledger_split",
    indexes = [
        Index(name = "idx_split_txn", columnList = "txn_id"),
        Index(name = "idx_split_account", columnList = "account_id"),
    ],
)
class LedgerSplit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "txn_id", nullable = false)
    var transaction: LedgerTxn? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var side: SplitSide = SplitSide.DEBIT,
    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_tag_id")
    var categoryTag: CategoryTag? = null,
    var memo: String? = null,
)
