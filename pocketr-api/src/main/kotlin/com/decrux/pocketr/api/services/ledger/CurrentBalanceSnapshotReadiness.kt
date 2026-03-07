package com.decrux.pocketr.api.services.ledger

import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface CurrentBalanceSnapshotReadiness {
    fun isSnapshotAllowed(accountId: UUID): Boolean

    object AlwaysAllowed : CurrentBalanceSnapshotReadiness {
        override fun isSnapshotAllowed(accountId: UUID): Boolean = true
    }
}

@Component
class CurrentBalanceSnapshotReadinessState : CurrentBalanceSnapshotReadiness {
    private val snapshotAvailable = AtomicBoolean(false)
    private val mismatchedAccountIds = AtomicReference<Set<UUID>>(emptySet())

    override fun isSnapshotAllowed(accountId: UUID): Boolean =
        snapshotAvailable.get() &&
            accountId !in mismatchedAccountIds.get()

    fun updateFromMismatchedAccounts(accountIds: Set<UUID>) {
        mismatchedAccountIds.set(accountIds.toSet())
        snapshotAvailable.set(true)
    }

    fun markUnavailable() {
        snapshotAvailable.set(false)
        mismatchedAccountIds.set(emptySet())
    }

    fun mismatchedAccountCount(): Int = mismatchedAccountIds.get().size

    fun isSnapshotAvailable(): Boolean = snapshotAvailable.get()

    fun mismatchedAccountsSnapshot(): Set<UUID> = mismatchedAccountIds.get()
}
