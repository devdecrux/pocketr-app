package com.decrux.pocketr.api.services.ledger

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

interface CurrentBalanceFastPathReadiness {
    fun isFastPathAllowed(): Boolean

    object AlwaysAllowed : CurrentBalanceFastPathReadiness {
        override fun isFastPathAllowed(): Boolean = true
    }
}

@Component
class CurrentBalanceFastPathReadinessState : CurrentBalanceFastPathReadiness {
    private val fastPathAllowed = AtomicBoolean(false)

    override fun isFastPathAllowed(): Boolean = fastPathAllowed.get()

    fun updateFromMismatchCount(mismatchCount: Long) {
        fastPathAllowed.set(mismatchCount == 0L)
    }

    fun markUnavailable() {
        fastPathAllowed.set(false)
    }
}
