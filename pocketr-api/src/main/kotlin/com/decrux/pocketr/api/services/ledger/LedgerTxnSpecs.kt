package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn
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
}
