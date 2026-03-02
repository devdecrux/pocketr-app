package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.AccountBalanceSummaryDto;
import com.decrux.pocketr.api.entities.dtos.AccountBalanceTimeseriesDto;
import com.decrux.pocketr.api.entities.dtos.MonthlyExpenseDto;
import com.decrux.pocketr.api.services.reporting.GenerateReport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ledger/reports")
public class ReportingController {

    private final GenerateReport generateReport;

    public ReportingController(GenerateReport generateReport) {
        this.generateReport = generateReport;
    }

    @GetMapping("/balances")
    public List<AccountBalanceSummaryDto> getAllAccountBalances(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @AuthenticationPrincipal User user
    ) {
        return generateReport.getAllAccountBalances(user, asOf != null ? asOf : LocalDate.now());
    }

    @GetMapping("/monthly")
    public List<MonthlyExpenseDto> getMonthlyExpenses(
            @RequestParam(defaultValue = "INDIVIDUAL") String mode,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
            @RequestParam(required = false) UUID householdId,
            @AuthenticationPrincipal User user
    ) {
        return generateReport.getMonthlyExpenses(user, period, mode, householdId);
    }

    @GetMapping("/timeseries")
    public AccountBalanceTimeseriesDto getBalanceTimeseries(
            @RequestParam UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @AuthenticationPrincipal User user
    ) {
        return generateReport.getBalanceTimeseries(accountId, dateFrom, dateTo, user);
    }
}
