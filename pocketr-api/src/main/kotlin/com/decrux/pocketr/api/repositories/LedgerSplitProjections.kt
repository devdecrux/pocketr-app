package com.decrux.pocketr.api.repositories

import java.time.LocalDate
import java.util.UUID

data class AccountRawBalanceProjection(
    val accountId: UUID,
    val rawBalance: Long,
)

data class MonthlyExpenseProjection(
    val expenseAccountId: UUID,
    val expenseAccountName: String,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
    val currency: String,
    val netMinor: Long,
)

data class DailyNetProjection(
    val txnDate: LocalDate,
    val netMinor: Long,
)
