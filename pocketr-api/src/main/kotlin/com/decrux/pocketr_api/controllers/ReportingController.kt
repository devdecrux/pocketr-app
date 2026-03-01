package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.AccountBalanceSummaryDto
import com.decrux.pocketr_api.entities.dtos.AccountBalanceTimeseriesDto
import com.decrux.pocketr_api.entities.dtos.MonthlyExpenseDto
import com.decrux.pocketr_api.services.reporting.GenerateReport
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/v1/ledger/reports")
class ReportingController(
    private val generateReport: GenerateReport,
) {
    @GetMapping("/balances")
    fun getAllAccountBalances(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOf: LocalDate?,
        @AuthenticationPrincipal user: User,
    ): List<AccountBalanceSummaryDto> = generateReport.getAllAccountBalances(user, asOf ?: LocalDate.now())

    @GetMapping("/monthly")
    fun getMonthlyExpenses(
        @RequestParam(defaultValue = "INDIVIDUAL") mode: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth,
        @RequestParam(required = false) householdId: UUID?,
        @AuthenticationPrincipal user: User,
    ): List<MonthlyExpenseDto> = generateReport.getMonthlyExpenses(user, period, mode, householdId)

    @GetMapping("/timeseries")
    fun getBalanceTimeseries(
        @RequestParam accountId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate,
        @AuthenticationPrincipal user: User,
    ): AccountBalanceTimeseriesDto = generateReport.getBalanceTimeseries(accountId, dateFrom, dateTo, user)
}
