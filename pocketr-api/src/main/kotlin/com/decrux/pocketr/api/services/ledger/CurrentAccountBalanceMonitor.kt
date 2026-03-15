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
    @Value("\${ledger.current-balance.integrity-log-on-startup:\${ledger.current-balance.reconciliation-log-on-startup:true}}")
    private val integrityLogOnStartup: Boolean,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun checkIntegrityOnStartup() {
        updateSnapshotIntegrityState()
    }

    /**
     * Manual trigger used in tests and operational troubleshooting.
     */
    fun logIntegrityStatusOnStartup() {
        updateSnapshotIntegrityState()
    }

    private fun updateSnapshotIntegrityState() {
        try {
            val mismatchedAccountIds = accountCurrentBalanceRepository.findAccountsBalanceMismatch().toSet()
            currentBalanceSnapshotReadinessState.updateFromMismatchedAccounts(mismatchedAccountIds)
            logIntegrityCheckResult(mismatchedAccountIds.size)
        } catch (ex: RuntimeException) {
            currentBalanceSnapshotReadinessState.markUnavailable()
            logger.error(
                "Failed to run the startup integrity check between snapshot raw balance and computed raw balance. Snapshot reads are now disabled for all accounts.",
                ex,
            )
        }
    }

    private fun logIntegrityCheckResult(mismatchCount: Int) {
        if (!integrityLogOnStartup && mismatchCount == 0) {
            return
        }

        if (mismatchCount == 0) {
            logger.info(
                "Startup integrity check passed: snapshot raw balance matches computed raw balance for all checked accounts. Snapshot reads remain available for all accounts.",
            )
            return
        }

        logger.info(
            "Startup integrity check found {} account(s) where snapshot raw balance differs from computed raw balance. Snapshot reads remain available, but are disabled for those {} mismatched account(s).",
            mismatchCount,
            currentBalanceSnapshotReadinessState.mismatchedAccountCount(),
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CurrentAccountBalanceMonitor::class.java)
    }
}
