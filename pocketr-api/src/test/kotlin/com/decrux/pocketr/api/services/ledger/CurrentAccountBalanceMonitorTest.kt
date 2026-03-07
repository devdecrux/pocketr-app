package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.*

@DisplayName("CurrentAccountBalanceMonitor")
class CurrentAccountBalanceMonitorTest {
    @Test
    @DisplayName("disables snapshot only for mismatched accounts")
    fun disablesSnapshotOnlyForMismatchedAccounts() {
        val repository = mock(AccountCurrentBalanceRepository::class.java)
        val readiness = CurrentBalanceSnapshotReadinessState()
        val monitor =
            CurrentAccountBalanceMonitor(
                accountCurrentBalanceRepository = repository,
                currentBalanceSnapshotReadinessState = readiness,
                reconciliationLogOnStartup = true,
            )
        val mismatchedAccountId = UUID.randomUUID()
        val healthyAccountId = UUID.randomUUID()

        `when`(repository.findAccountsBalanceMismatch()).thenReturn(listOf(mismatchedAccountId))

        monitor.logMismatchCountOnStartup()

        assertTrue(readiness.isSnapshotAvailable())
        assertFalse(readiness.isSnapshotAllowed(mismatchedAccountId))
        assertTrue(readiness.isSnapshotAllowed(healthyAccountId))
    }

    @Test
    @DisplayName("marks snapshot unavailable when reconciliation fails")
    fun marksSnapshotUnavailableWhenReconciliationFails() {
        val repository = mock(AccountCurrentBalanceRepository::class.java)
        val readiness = CurrentBalanceSnapshotReadinessState()
        val monitor =
            CurrentAccountBalanceMonitor(
                accountCurrentBalanceRepository = repository,
                currentBalanceSnapshotReadinessState = readiness,
                reconciliationLogOnStartup = true,
            )

        `when`(repository.findAccountsBalanceMismatch()).thenThrow(RuntimeException("reconciliation failed"))

        monitor.logMismatchCountOnStartup()

        assertFalse(readiness.isSnapshotAvailable())
        assertFalse(readiness.isSnapshotAllowed(UUID.randomUUID()))
    }
}
