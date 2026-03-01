package com.decrux.pocketr_api.services.reporting

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.AccountBalanceSummaryDto
import com.decrux.pocketr_api.entities.dtos.AccountBalanceTimeseriesDto
import com.decrux.pocketr_api.entities.dtos.MonthlyExpenseDto
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface GenerateReport {
    fun getMonthlyExpenses(
        user: User,
        period: YearMonth,
        mode: String,
        householdId: UUID?,
    ): List<MonthlyExpenseDto>

    fun getAllAccountBalances(
        user: User,
        asOf: LocalDate,
    ): List<AccountBalanceSummaryDto>

    fun getBalanceTimeseries(
        accountId: UUID,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        user: User,
    ): AccountBalanceTimeseriesDto
}
