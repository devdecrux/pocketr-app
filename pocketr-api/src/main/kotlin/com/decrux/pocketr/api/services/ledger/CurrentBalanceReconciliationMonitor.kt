package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CurrentBalanceReconciliationMonitor(
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    private val currentBalanceSnapshotReadinessState: CurrentBalanceSnapshotReadinessState,
    @Value("\${ledger.current-balance.reconciliation-log-on-startup:true}")
    private val reconciliationLogOnStartup: Boolean,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun logMismatchCountOnStartup() {
        try {
            val mismatchCount = accountCurrentBalanceRepository.countReconciliationMismatches()
            currentBalanceSnapshotReadinessState.updateFromMismatchCount(mismatchCount)

            if (reconciliationLogOnStartup || mismatchCount > 0) {
                logger.info(
                    "reconciliation_mismatch={} reconciliation_mismatch_count={} snapshot_allowed={}",
                    mismatchCount > 0,
                    mismatchCount,
                    currentBalanceSnapshotReadinessState.isSnapshotAllowed(),
                )
            }
        } catch (ex: RuntimeException) {
            currentBalanceSnapshotReadinessState.markUnavailable()
            logger.error(
                "reconciliation_check_failed=true snapshot_allowed={}",
                currentBalanceSnapshotReadinessState.isSnapshotAllowed(),
                ex,
            )
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CurrentBalanceReconciliationMonitor::class.java)
    }
}
