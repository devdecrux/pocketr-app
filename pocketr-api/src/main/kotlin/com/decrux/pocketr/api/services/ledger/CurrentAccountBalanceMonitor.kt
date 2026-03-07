package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Monitors snapshot-cache consistency for account balances at application startup.
 *
 * It compares cached balances with balances computed from ledger entries and updates
 * snapshot-read readiness so the service can fall back to computed balances only for accounts
 * that are out of sync, while keeping snapshot reads active for healthy accounts.
 */
@Component
class CurrentAccountBalanceMonitor(
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    private val currentBalanceSnapshotReadinessState: CurrentBalanceSnapshotReadinessState,
    @Value("\${ledger.current-balance.reconciliation-log-on-startup:true}")
    private val reconciliationLogOnStartup: Boolean,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun reconcileOnStartup() {
        reconcileSnapshotReadiness()
    }

    /**
     * Manual trigger used in tests and operational troubleshooting.
     */
    fun logMismatchCountOnStartup() {
        reconcileSnapshotReadiness()
    }

    private fun reconcileSnapshotReadiness() {
        try {
            val mismatchedAccountIds = accountCurrentBalanceRepository.findAccountsBalanceMismatch().toSet()
            currentBalanceSnapshotReadinessState.updateFromMismatchedAccounts(mismatchedAccountIds)
            logReconciliationResult(mismatchedAccountIds.size)
        } catch (ex: RuntimeException) {
            currentBalanceSnapshotReadinessState.markUnavailable()
            logger.error(
                "reconciliation_check_failed=true snapshot_available={}",
                currentBalanceSnapshotReadinessState.isSnapshotAvailable(),
                ex,
            )
        }
    }

    private fun logReconciliationResult(mismatchCount: Int) {
        if (!reconciliationLogOnStartup && mismatchCount == 0) {
            return
        }

        logger.info(
            "reconciliation_mismatch={} reconciliation_mismatch_count={} snapshot_available={} snapshot_disabled_account_count={}",
            mismatchCount > 0,
            mismatchCount,
            currentBalanceSnapshotReadinessState.isSnapshotAvailable(),
            currentBalanceSnapshotReadinessState.mismatchedAccountCount(),
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CurrentAccountBalanceMonitor::class.java)
    }
}
