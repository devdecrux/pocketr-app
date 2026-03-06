package com.decrux.pocketr.api.services.ledger

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

interface CurrentBalanceSnapshotReadiness {
    fun isSnapshotAllowed(): Boolean

    object AlwaysAllowed : CurrentBalanceSnapshotReadiness {
        override fun isSnapshotAllowed(): Boolean = true
    }
}

@Component
class CurrentBalanceSnapshotReadinessState : CurrentBalanceSnapshotReadiness {
    private val snapshotAllowed = AtomicBoolean(false)

    override fun isSnapshotAllowed(): Boolean = snapshotAllowed.get()

    fun updateFromMismatchCount(mismatchCount: Long) {
        snapshotAllowed.set(mismatchCount == 0L)
    }

    fun markUnavailable() {
        snapshotAllowed.set(false)
    }
}
