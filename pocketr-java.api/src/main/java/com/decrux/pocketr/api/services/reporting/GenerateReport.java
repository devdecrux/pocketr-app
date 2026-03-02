package com.decrux.pocketr.api.services.reporting;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.AccountBalanceSummaryDto;
import com.decrux.pocketr.api.entities.dtos.AccountBalanceTimeseriesDto;
import com.decrux.pocketr.api.entities.dtos.MonthlyExpenseDto;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface GenerateReport {

    List<MonthlyExpenseDto> getMonthlyExpenses(User user, YearMonth period, String mode, UUID householdId);

    List<AccountBalanceSummaryDto> getAllAccountBalances(User user, LocalDate asOf);

    AccountBalanceTimeseriesDto getBalanceTimeseries(UUID accountId, LocalDate dateFrom, LocalDate dateTo, User user);
}
