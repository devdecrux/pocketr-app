package com.decrux.pocketr.api.entities.db.ledger;

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
import java.util.UUID;

@Entity
@Table(
    name = "ledger_split",
    indexes = {
        @Index(name = "idx_split_txn", columnList = "txn_id"),
        @Index(name = "idx_split_account", columnList = "account_id")
    }
)
public class LedgerSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "txn_id", nullable = false)
    private LedgerTxn transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitSide side = SplitSide.DEBIT;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_tag_id")
    private CategoryTag categoryTag;

    public LedgerSplit() {
    }

    public LedgerSplit(
        UUID id,
        LedgerTxn transaction,
        Account account,
        SplitSide side,
        long amountMinor,
        CategoryTag categoryTag
    ) {
        this.id = id;
        this.transaction = transaction;
        this.account = account;
        this.side = side;
        this.amountMinor = amountMinor;
        this.categoryTag = categoryTag;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LedgerTxn getTransaction() {
        return transaction;
    }

    public void setTransaction(LedgerTxn transaction) {
        this.transaction = transaction;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public SplitSide getSide() {
        return side;
    }

    public void setSide(SplitSide side) {
        this.side = side;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public CategoryTag getCategoryTag() {
        return categoryTag;
    }

    public void setCategoryTag(CategoryTag categoryTag) {
        this.categoryTag = categoryTag;
    }
}
