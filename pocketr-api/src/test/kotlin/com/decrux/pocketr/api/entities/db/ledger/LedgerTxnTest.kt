package com.decrux.pocketr.api.entities.db.ledger

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LedgerTxnTest {
    @Test
    fun onCreateRefreshesUpdatedAt() {
        val originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z")
        val txn = LedgerTxn(updatedAt = originalUpdatedAt)

        txn.onCreate()

        assertTrue(txn.updatedAt.isAfter(originalUpdatedAt))
    }

    @Test
    fun onUpdateRefreshesUpdatedAt() {
        val originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z")
        val txn = LedgerTxn(updatedAt = originalUpdatedAt)

        txn.onUpdate()

        assertTrue(txn.updatedAt.isAfter(originalUpdatedAt))
    }
}
