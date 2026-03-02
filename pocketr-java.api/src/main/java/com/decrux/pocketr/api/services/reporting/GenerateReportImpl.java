package com.decrux.pocketr.api.services.reporting;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.entities.dtos.AccountBalanceSummaryDto;
import com.decrux.pocketr.api.entities.dtos.AccountBalanceTimeseriesDto;
import com.decrux.pocketr.api.entities.dtos.BalanceTimeseriesPointDto;
import com.decrux.pocketr.api.entities.dtos.MonthlyExpenseDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.LedgerSplitRepository;
import com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerateReportImpl implements GenerateReport {

    private static final String MODE_INDIVIDUAL = "INDIVIDUAL";
    private static final String MODE_HOUSEHOLD = "HOUSEHOLD";
    private static final Set<AccountType> DEBIT_NORMAL_TYPES = Set.of(AccountType.ASSET, AccountType.EXPENSE);

    private final LedgerSplitRepository ledgerSplitRepository;
    private final AccountRepository accountRepository;
    private final ManageHousehold manageHousehold;

    public GenerateReportImpl(
        LedgerSplitRepository ledgerSplitRepository,
        AccountRepository accountRepository,
        ManageHousehold manageHousehold
    ) {
        this.ledgerSplitRepository = ledgerSplitRepository;
        this.accountRepository = accountRepository;
        this.manageHousehold = manageHousehold;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyExpenseDto> getMonthlyExpenses(User user, YearMonth period, String mode, UUID householdId) {
        LocalDate monthStart = period.atDay(1);
        LocalDate monthEnd = period.plusMonths(1).atDay(1);

        List<MonthlyExpenseProjection> rows;
        switch (mode.toUpperCase(Locale.ROOT)) {
            case MODE_INDIVIDUAL -> {
                long userId = requireNotNull(user.getUserId(), "User ID must not be null");
                rows = ledgerSplitRepository.monthlyExpensesByUser(
                    userId,
                    monthStart,
                    monthEnd,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                );
            }
            case MODE_HOUSEHOLD -> {
                UUID hId = householdId;
                if (hId == null) {
                    throw new BadRequestException("householdId is required for household mode");
                }
                long userId = requireNotNull(user.getUserId(), "User ID must not be null");
                if (!manageHousehold.isActiveMember(hId, userId)) {
                    throw new ForbiddenException("Not an active member of this household");
                }
                rows = ledgerSplitRepository.monthlyExpensesByHousehold(
                    hId,
                    monthStart,
                    monthEnd,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                );
            }
            default -> throw new BadRequestException("Invalid mode: " + mode + ". Must be INDIVIDUAL or HOUSEHOLD");
        }

        return rows.stream().map(row -> new MonthlyExpenseDto(
            row.getExpenseAccountId(),
            row.getExpenseAccountName(),
            row.getCategoryTagId(),
            row.getCategoryTagName(),
            row.getCurrency(),
            row.getNetMinor()
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBalanceSummaryDto> getAllAccountBalances(User user, LocalDate asOf) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        var accounts = accountRepository.findByOwnerUserId(userId);

        return accounts.stream().map(account -> {
            UUID accountId = requireNotNull(account.getId());
            boolean isDebitNormal = DEBIT_NORMAL_TYPES.contains(account.getType());
            long balanceMinor = isDebitNormal
                ? ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)
                : ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.CREDIT, SplitSide.DEBIT);

            return new AccountBalanceSummaryDto(
                accountId,
                account.getName(),
                account.getType().name(),
                requireNotNull(account.getCurrency() != null ? account.getCurrency().getCode() : null),
                balanceMinor
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalanceTimeseriesDto getBalanceTimeseries(UUID accountId, LocalDate dateFrom, LocalDate dateTo, User user) {
        if (dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("dateFrom must be before or equal to dateTo");
        }

        long userId = requireNotNull(user.getUserId(), "User ID must not be null");

        var account = accountRepository.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found"));

        if (!Objects.equals(account.getOwner() != null ? account.getOwner().getUserId() : null, userId)) {
            throw new ForbiddenException("Not the owner of this account");
        }

        boolean isDebitNormal = DEBIT_NORMAL_TYPES.contains(account.getType());
        SplitSide positive = isDebitNormal ? SplitSide.DEBIT : SplitSide.CREDIT;
        SplitSide negative = isDebitNormal ? SplitSide.CREDIT : SplitSide.DEBIT;

        long openingBalance = ledgerSplitRepository.computeBalance(accountId, dateFrom.minusDays(1), positive, negative);

        var dailyNets = ledgerSplitRepository.dailyNetByAccount(accountId, dateFrom, dateTo, positive, negative);
        Map<LocalDate, Long> dailyNetMap = dailyNets
            .stream()
            .collect(Collectors.toMap(net -> net.getTxnDate(), net -> net.getNetMinor()));

        List<BalanceTimeseriesPointDto> points = new ArrayList<>();
        long runningBalance = openingBalance;
        LocalDate currentDate = dateFrom;
        while (!currentDate.isAfter(dateTo)) {
            runningBalance += dailyNetMap.getOrDefault(currentDate, 0L);
            points.add(new BalanceTimeseriesPointDto(currentDate, runningBalance));
            currentDate = currentDate.plusDays(1);
        }

        return new AccountBalanceTimeseriesDto(
            requireNotNull(account.getId()),
            account.getName(),
            account.getType().name(),
            requireNotNull(account.getCurrency() != null ? account.getCurrency().getCode() : null),
            points
        );
    }

    private static <T> T requireNotNull(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Required value was null.");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
