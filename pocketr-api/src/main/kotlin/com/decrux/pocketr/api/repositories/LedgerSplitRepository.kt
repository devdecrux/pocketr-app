package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.repositories.projections.AccountRawBalanceProjection
import com.decrux.pocketr.api.repositories.projections.DailyNetProjection
import com.decrux.pocketr.api.repositories.projections.LiabilityPaymentProjection
import com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface LedgerSplitRepository : JpaRepository<LedgerSplit, UUID> {
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
             - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        FROM LedgerSplit ls
        WHERE ls.account.id = :accountId
          AND ls.transaction.txnDate <= :asOf
        """,
    )
    fun computeBalance(
        accountId: UUID,
        asOf: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
             - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        FROM LedgerSplit ls
        WHERE ls.account.id = :accountId
          AND ls.transaction.txnDate >= :dateFrom
          AND ls.transaction.txnDate <= :asOf
        """,
    )
    fun computeBalanceBetween(
        accountId: UUID,
        dateFrom: LocalDate,
        asOf: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): Long

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.AccountRawBalanceProjection(
            ls.account.id,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        )
        FROM LedgerSplit ls
        WHERE ls.account.id IN :accountIds
          AND ls.transaction.txnDate <= :asOf
        GROUP BY ls.account.id
        """,
    )
    fun computeRawBalancesByAccountIds(
        accountIds: Collection<UUID>,
        asOf: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): List<AccountRawBalanceProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.AccountRawBalanceProjection(
            ls.account.id,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        )
        FROM LedgerSplit ls
        WHERE ls.account.id IN :accountIds
          AND ls.transaction.txnDate >= :dateFrom
          AND ls.transaction.txnDate <= :asOf
        GROUP BY ls.account.id
        """,
    )
    fun computeRawBalancesByAccountIdsBetween(
        accountIds: Collection<UUID>,
        dateFrom: LocalDate,
        asOf: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): List<AccountRawBalanceProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection(
            a.id,
            a.name,
            ct.id,
            ct.name,
            a.currency.code,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0),
            ct.color
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.EXPENSE
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND a.owner.userId = :userId
        GROUP BY a.id, a.name, ct.id, ct.name, ct.color, a.currency.code
        ORDER BY a.name, ct.name
        """,
    )
    fun monthlyExpensesByUser(
        userId: Long,
        monthStart: LocalDate,
        monthEnd: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): List<MonthlyExpenseProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.LiabilityPaymentProjection(
            a.id,
            a.name,
            a.currency.code,
            COALESCE(SUM(ls.amountMinor), 0)
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.LIABILITY
          AND ls.side = :liabilityDebit
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND a.owner.userId = :userId
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit counterSplit
              JOIN counterSplit.account counterAccount
              WHERE counterSplit.transaction = ls.transaction
                AND counterSplit.side = :assetCredit
                AND counterAccount.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.ASSET
          )
        GROUP BY a.id, a.name, a.currency.code
        ORDER BY a.name
        """,
    )
    fun monthlyLiabilityPaymentsByUser(
        userId: Long,
        monthStart: LocalDate,
        monthEnd: LocalDate,
        liabilityDebit: SplitSide,
        assetCredit: SplitSide,
    ): List<LiabilityPaymentProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection(
            a.id,
            a.name,
            ct.id,
            ct.name,
            a.currency.code,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0),
            ct.color
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.EXPENSE
          AND a.owner.userId = :userId
        GROUP BY a.id, a.name, ct.id, ct.name, ct.color, a.currency.code
        ORDER BY a.name, ct.name
        """,
    )
    fun lifetimeExpensesByUser(
        userId: Long,
        debit: SplitSide,
        credit: SplitSide,
    ): List<MonthlyExpenseProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.LiabilityPaymentProjection(
            a.id,
            a.name,
            a.currency.code,
            COALESCE(SUM(ls.amountMinor), 0)
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.LIABILITY
          AND ls.side = :liabilityDebit
          AND a.owner.userId = :userId
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit counterSplit
              JOIN counterSplit.account counterAccount
              WHERE counterSplit.transaction = ls.transaction
                AND counterSplit.side = :assetCredit
                AND counterAccount.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.ASSET
          )
        GROUP BY a.id, a.name, a.currency.code
        ORDER BY a.name
        """,
    )
    fun lifetimeLiabilityPaymentsByUser(
        userId: Long,
        liabilityDebit: SplitSide,
        assetCredit: SplitSide,
    ): List<LiabilityPaymentProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection(
            a.id,
            a.name,
            ct.id,
            ct.name,
            a.currency.code,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0),
            ct.color
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.EXPENSE
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit visibleSplit
              JOIN visibleSplit.account visibleAccount
              WHERE visibleSplit.transaction = ls.transaction
                AND visibleAccount.id IN :sharedAccountIds
          )
        GROUP BY a.id, a.name, ct.id, ct.name, ct.color, a.currency.code
        ORDER BY a.name, ct.name
        """,
    )
    fun monthlyExpensesByHousehold(
        sharedAccountIds: Set<UUID>,
        monthStart: LocalDate,
        monthEnd: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): List<MonthlyExpenseProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.LiabilityPaymentProjection(
            a.id,
            a.name,
            a.currency.code,
            COALESCE(SUM(ls.amountMinor), 0)
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.LIABILITY
          AND ls.side = :liabilityDebit
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit visibleSplit
              JOIN visibleSplit.account visibleAccount
              WHERE visibleSplit.transaction = ls.transaction
                AND visibleAccount.id IN :sharedAccountIds
          )
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit counterSplit
              JOIN counterSplit.account counterAccount
              WHERE counterSplit.transaction = ls.transaction
                AND counterSplit.side = :assetCredit
                AND counterAccount.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.ASSET
          )
        GROUP BY a.id, a.name, a.currency.code
        ORDER BY a.name
        """,
    )
    fun monthlyLiabilityPaymentsByHousehold(
        sharedAccountIds: Set<UUID>,
        monthStart: LocalDate,
        monthEnd: LocalDate,
        liabilityDebit: SplitSide,
        assetCredit: SplitSide,
    ): List<LiabilityPaymentProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection(
            a.id,
            a.name,
            ct.id,
            ct.name,
            a.currency.code,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0),
            ct.color
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.EXPENSE
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit visibleSplit
              JOIN visibleSplit.account visibleAccount
              WHERE visibleSplit.transaction = ls.transaction
                AND visibleAccount.id IN :sharedAccountIds
          )
        GROUP BY a.id, a.name, ct.id, ct.name, ct.color, a.currency.code
        ORDER BY a.name, ct.name
        """,
    )
    fun lifetimeExpensesByHousehold(
        sharedAccountIds: Set<UUID>,
        debit: SplitSide,
        credit: SplitSide,
    ): List<MonthlyExpenseProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.LiabilityPaymentProjection(
            a.id,
            a.name,
            a.currency.code,
            COALESCE(SUM(ls.amountMinor), 0)
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.LIABILITY
          AND ls.side = :liabilityDebit
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit visibleSplit
              JOIN visibleSplit.account visibleAccount
              WHERE visibleSplit.transaction = ls.transaction
                AND visibleAccount.id IN :sharedAccountIds
          )
          AND EXISTS (
              SELECT 1
              FROM LedgerSplit counterSplit
              JOIN counterSplit.account counterAccount
              WHERE counterSplit.transaction = ls.transaction
                AND counterSplit.side = :assetCredit
                AND counterAccount.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.ASSET
          )
        GROUP BY a.id, a.name, a.currency.code
        ORDER BY a.name
        """,
    )
    fun lifetimeLiabilityPaymentsByHousehold(
        sharedAccountIds: Set<UUID>,
        liabilityDebit: SplitSide,
        assetCredit: SplitSide,
    ): List<LiabilityPaymentProjection>

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.DailyNetProjection(
            ls.transaction.txnDate,
            COALESCE(SUM(CASE WHEN ls.side = :positive THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :negative THEN ls.amountMinor ELSE 0 END), 0)
        )
        FROM LedgerSplit ls
        WHERE ls.account.id = :accountId
          AND ls.transaction.txnDate >= :dateFrom
          AND ls.transaction.txnDate <= :dateTo
        GROUP BY ls.transaction.txnDate
        ORDER BY ls.transaction.txnDate
        """,
    )
    fun dailyNetByAccount(
        accountId: UUID,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        positive: SplitSide,
        negative: SplitSide,
    ): List<DailyNetProjection>
}
