package com.decrux.pocketr_api.entities.dtos

import java.time.LocalDate
import java.util.UUID

data class MonthlyExpenseDto(
    val expenseAccountId: UUID,
    val expenseAccountName: String,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
    val currency: String,
    val netMinor: Long,
)

data class AccountBalanceSummaryDto(
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val currency: String,
    val balanceMinor: Long,
    val isArchived: Boolean,
)

data class BalanceTimeseriesPointDto(
    val date: LocalDate,
    val balanceMinor: Long,
)

data class AccountBalanceTimeseriesDto(
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val currency: String,
    val points: List<BalanceTimeseriesPointDto>,
)
