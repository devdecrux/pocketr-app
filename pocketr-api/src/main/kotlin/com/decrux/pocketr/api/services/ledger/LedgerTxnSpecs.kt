package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.util.UUID

object LedgerTxnSpecs {
    fun forUser(userId: Long): Specification<LedgerTxn> =
        Specification { root, _, cb ->
            cb.equal(root.get<Any>("createdBy").get<Long>("userId"), userId)
        }

    fun dateFrom(date: LocalDate): Specification<LedgerTxn> =
        Specification { root, _, cb ->
            cb.greaterThanOrEqualTo(root.get("txnDate"), date)
        }

    fun dateTo(date: LocalDate): Specification<LedgerTxn> =
        Specification { root, _, cb ->
            cb.lessThanOrEqualTo(root.get("txnDate"), date)
        }

    fun hasAccount(accountId: UUID): Specification<LedgerTxn> =
        Specification { root, query, cb ->
            val splits = root.join<Any, Any>("splits")
            query.distinct(true)
            cb.equal(splits.get<Any>("account").get<UUID>("id"), accountId)
        }

    fun hasCategory(categoryId: UUID): Specification<LedgerTxn> =
        Specification { root, query, cb ->
            val splits = root.join<Any, Any>("splits")
            query.distinct(true)
            cb.equal(splits.get<Any>("categoryTag").get<UUID>("id"), categoryId)
        }

    fun hasAnySharedAccount(accountIds: Set<UUID>): Specification<LedgerTxn> =
        Specification { root, query, cb ->
            query.distinct(true)
            val splits = root.join<Any, Any>("splits")
            splits.get<Any>("account").get<UUID>("id").`in`(accountIds)
        }

    fun spendingOnly(): Specification<LedgerTxn> =
        Specification { root, query, cb ->
            query.distinct(true)

            val expenseSplit = query.subquery(Long::class.java)
            val expenseSplitRoot = expenseSplit.from(LedgerSplit::class.java)
            expenseSplit.select(cb.literal(1L))
            expenseSplit.where(
                cb.equal(expenseSplitRoot.get<Any>("transaction").get<UUID>("id"), root.get<UUID>("id")),
                cb.equal(expenseSplitRoot.get<Any>("account").get<AccountType>("type"), AccountType.EXPENSE),
            )

            val assetCreditSplit = query.subquery(Long::class.java)
            val assetCreditSplitRoot = assetCreditSplit.from(LedgerSplit::class.java)
            assetCreditSplit.select(cb.literal(1L))
            assetCreditSplit.where(
                cb.equal(assetCreditSplitRoot.get<Any>("transaction").get<UUID>("id"), root.get<UUID>("id")),
                cb.equal(assetCreditSplitRoot.get<Any>("account").get<AccountType>("type"), AccountType.ASSET),
                cb.equal(assetCreditSplitRoot.get<SplitSide>("side"), SplitSide.CREDIT),
            )

            val liabilityDebitSplit = query.subquery(Long::class.java)
            val liabilityDebitSplitRoot = liabilityDebitSplit.from(LedgerSplit::class.java)
            liabilityDebitSplit.select(cb.literal(1L))
            liabilityDebitSplit.where(
                cb.equal(liabilityDebitSplitRoot.get<Any>("transaction").get<UUID>("id"), root.get<UUID>("id")),
                cb.equal(liabilityDebitSplitRoot.get<Any>("account").get<AccountType>("type"), AccountType.LIABILITY),
                cb.equal(liabilityDebitSplitRoot.get<SplitSide>("side"), SplitSide.DEBIT),
            )

            val nonDebtPaymentSplit = query.subquery(Long::class.java)
            val nonDebtPaymentSplitRoot = nonDebtPaymentSplit.from(LedgerSplit::class.java)
            nonDebtPaymentSplit.select(cb.literal(1L))
            nonDebtPaymentSplit.where(
                cb.equal(nonDebtPaymentSplitRoot.get<Any>("transaction").get<UUID>("id"), root.get<UUID>("id")),
                cb.not(
                    nonDebtPaymentSplitRoot
                        .get<Any>("account")
                        .get<AccountType>("type")
                        .`in`(listOf(AccountType.ASSET, AccountType.LIABILITY)),
                ),
            )

            cb.or(
                cb.exists(expenseSplit),
                cb.and(
                    cb.exists(assetCreditSplit),
                    cb.exists(liabilityDebitSplit),
                    cb.not(cb.exists(nonDebtPaymentSplit)),
                ),
            )
        }
}
