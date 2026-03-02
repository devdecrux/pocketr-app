package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.repositories.projections.AccountRawBalanceProjection;
import com.decrux.pocketr.api.repositories.projections.DailyNetProjection;
import com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerSplitRepository extends JpaRepository<LedgerSplit, UUID> {

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
             - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        FROM LedgerSplit ls
        WHERE ls.account.id = :accountId
          AND ls.transaction.txnDate <= :asOf
        """
    )
    long computeBalance(
        @Param("accountId") UUID accountId,
        @Param("asOf") LocalDate asOf,
        @Param("debit") SplitSide debit,
        @Param("credit") SplitSide credit
    );

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
        """
    )
    List<AccountRawBalanceProjection> computeRawBalancesByAccountIds(
        @Param("accountIds") Collection<UUID> accountIds,
        @Param("asOf") LocalDate asOf,
        @Param("debit") SplitSide debit,
        @Param("credit") SplitSide credit
    );

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection(
            a.id,
            a.name,
            ct.id,
            ct.name,
            a.currency.code,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.EXPENSE
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND a.owner.userId = :userId
        GROUP BY a.id, a.name, ct.id, ct.name, a.currency.code
        ORDER BY a.name, ct.name
        """
    )
    List<MonthlyExpenseProjection> monthlyExpensesByUser(
        @Param("userId") long userId,
        @Param("monthStart") LocalDate monthStart,
        @Param("monthEnd") LocalDate monthEnd,
        @Param("debit") SplitSide debit,
        @Param("credit") SplitSide credit
    );

    @Query(
        """
        SELECT NEW com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection(
            a.id,
            a.name,
            ct.id,
            ct.name,
            a.currency.code,
            COALESCE(SUM(CASE WHEN ls.side = :debit THEN ls.amountMinor ELSE 0 END), 0)
          - COALESCE(SUM(CASE WHEN ls.side = :credit THEN ls.amountMinor ELSE 0 END), 0)
        )
        FROM LedgerSplit ls
        JOIN ls.account a
        LEFT JOIN ls.categoryTag ct
        WHERE a.type = com.decrux.pocketr.api.entities.db.ledger.AccountType.EXPENSE
          AND ls.transaction.txnDate >= :monthStart
          AND ls.transaction.txnDate < :monthEnd
          AND ls.transaction.householdId = :householdId
        GROUP BY a.id, a.name, ct.id, ct.name, a.currency.code
        ORDER BY a.name, ct.name
        """
    )
    List<MonthlyExpenseProjection> monthlyExpensesByHousehold(
        @Param("householdId") UUID householdId,
        @Param("monthStart") LocalDate monthStart,
        @Param("monthEnd") LocalDate monthEnd,
        @Param("debit") SplitSide debit,
        @Param("credit") SplitSide credit
    );

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
        """
    )
    List<DailyNetProjection> dailyNetByAccount(
        @Param("accountId") UUID accountId,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo,
        @Param("positive") SplitSide positive,
        @Param("negative") SplitSide negative
    );
}
