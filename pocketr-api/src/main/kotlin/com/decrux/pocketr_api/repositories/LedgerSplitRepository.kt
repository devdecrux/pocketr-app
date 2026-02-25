package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.ledger.LedgerSplit
import com.decrux.pocketr_api.entities.db.ledger.SplitSide
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
    fun computeBalance(accountId: UUID, asOf: LocalDate, debit: SplitSide, credit: SplitSide): Long

    @Query(
        """
        SELECT ls.account.id,
               COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
             - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
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
    ): List<Array<Any>>

    @Query(
        """
        SELECT a.id, a.name, ct.id, ct.name, a.currency.code,
               SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END)
             - SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END)
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr_api.entities.db.ledger.AccountType.EXPENSE
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND a.owner.userId = :userId
        GROUP BY a.id, a.name, ct.id, ct.name, a.currency.code
        ORDER BY a.name, ct.name
        """,
    )
    fun monthlyExpensesByUser(
        userId: Long,
        monthStart: LocalDate,
        monthEnd: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): List<Array<Any?>>

    @Query(
        """
        SELECT a.id, a.name, ct.id, ct.name, a.currency.code,
               SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END)
             - SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END)
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr_api.entities.db.ledger.AccountType.EXPENSE
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND ls.transaction.householdId = :householdId
        GROUP BY a.id, a.name, ct.id, ct.name, a.currency.code
        ORDER BY a.name, ct.name
        """,
    )
    fun monthlyExpensesByHousehold(
        householdId: UUID,
        monthStart: LocalDate,
        monthEnd: LocalDate,
        debit: SplitSide,
        credit: SplitSide,
    ): List<Array<Any?>>

    @Query(
        """
        SELECT ls.transaction.txnDate,
               SUM(CASE WHEN ls.side = :positive THEN ls.amountMinor ELSE 0 END)
             - SUM(CASE WHEN ls.side = :negative THEN ls.amountMinor ELSE 0 END)
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
    ): List<Array<Any>>
}
