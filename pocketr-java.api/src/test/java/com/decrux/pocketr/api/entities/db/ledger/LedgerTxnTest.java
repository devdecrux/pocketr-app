package com.decrux.pocketr.api.entities.db.ledger;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTxnTest {

    @Test
    void onCreateRefreshesUpdatedAt() {
        Instant originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        LedgerTxn txn = new LedgerTxn();
        txn.setUpdatedAt(originalUpdatedAt);

        txn.onCreate();

        assertTrue(txn.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void onUpdateRefreshesUpdatedAt() {
        Instant originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        LedgerTxn txn = new LedgerTxn();
        txn.setUpdatedAt(originalUpdatedAt);

        txn.onUpdate();

        assertTrue(txn.getUpdatedAt().isAfter(originalUpdatedAt));
    }
}
