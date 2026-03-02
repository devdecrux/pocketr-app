package com.decrux.pocketr.api.services.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.LedgerSplitRepository;
import com.decrux.pocketr.api.repositories.projections.DailyNetProjection;
import com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GenerateReportImpl.
 *
 * Tests balance computation, monthly expense summaries, individual vs household
 * mode filtering, and timeseries generation with mocked repositories.
 *
 * User entity uses Long IDs (not UUID).
 */
@DisplayName("GenerateReportImpl - Reporting")
class ReportingTest {

    private LedgerSplitRepository ledgerSplitRepository;
    private AccountRepository accountRepository;
    private ManageHousehold manageHousehold;
    private GenerateReportImpl service;

    private final Currency eur = new Currency("EUR", (short) 2, "Euro");

    private final User userA = createUser(1L, "alice@test.com");
    private final User userB = createUser(2L, "bob@test.com");

    private final UUID checkingId = UUID.randomUUID();
    private final UUID savingsId = UUID.randomUUID();
    private final UUID groceriesId = UUID.randomUUID();
    private final UUID utilitiesId = UUID.randomUUID();
    private final UUID salaryId = UUID.randomUUID();
    private final UUID mortgageId = UUID.randomUUID();
    private final UUID equityId = UUID.randomUUID();

    private final Account checking = new Account(checkingId, userA, "Checking", AccountType.ASSET, eur, Instant.now());
    private final Account savings = new Account(savingsId, userA, "Savings", AccountType.ASSET, eur, Instant.now());
    private final Account groceries = new Account(groceriesId, userA, "Groceries", AccountType.EXPENSE, eur, Instant.now());
    private final Account utilities = new Account(utilitiesId, userA, "Utilities", AccountType.EXPENSE, eur, Instant.now());
    private final Account salary = new Account(salaryId, userA, "Salary", AccountType.INCOME, eur, Instant.now());
    private final Account mortgage = new Account(mortgageId, userA, "Mortgage", AccountType.LIABILITY, eur, Instant.now());
    private final Account equity = new Account(equityId, userA, "Opening Equity", AccountType.EQUITY, eur, Instant.now());

    @BeforeEach
    void setUp() {
        ledgerSplitRepository = mock(LedgerSplitRepository.class);
        accountRepository = mock(AccountRepository.class);
        manageHousehold = mock(ManageHousehold.class);
        service = new GenerateReportImpl(ledgerSplitRepository, accountRepository, manageHousehold);
    }

    @Nested
    @DisplayName("Account balance summaries")
    class BalanceComputation {

        @Test
        @DisplayName("should compute correct balances for all account types")
        void computeBalancesForAllAccountTypes() {
            List<Account> allAccounts = List.of(checking, savings, groceries, utilities, salary, mortgage, equity);
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(allAccounts);

            LocalDate asOf = LocalDate.of(2026, 2, 15);

            when(ledgerSplitRepository.computeBalance(checkingId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(190000L);
            when(ledgerSplitRepository.computeBalance(savingsId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(50000L);
            when(ledgerSplitRepository.computeBalance(groceriesId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(8500L);
            when(ledgerSplitRepository.computeBalance(utilitiesId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(4500L);
            when(ledgerSplitRepository.computeBalance(salaryId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)).thenReturn(400000L);
            when(ledgerSplitRepository.computeBalance(mortgageId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)).thenReturn(49950000L);
            when(ledgerSplitRepository.computeBalance(equityId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)).thenReturn(150000L);

            var result = service.getAllAccountBalances(userA, asOf);

            assertEquals(7, result.size());

            var checkingResult = result.stream().filter(it -> checkingId.equals(it.getAccountId())).findFirst().orElseThrow();
            assertEquals(190000L, checkingResult.getBalanceMinor());
            assertEquals("ASSET", checkingResult.getAccountType());
            assertEquals("EUR", checkingResult.getCurrency());

            var mortgageResult = result.stream().filter(it -> mortgageId.equals(it.getAccountId())).findFirst().orElseThrow();
            assertEquals(49950000L, mortgageResult.getBalanceMinor());
            assertEquals("LIABILITY", mortgageResult.getAccountType());

            var salaryResult = result.stream().filter(it -> salaryId.equals(it.getAccountId())).findFirst().orElseThrow();
            assertEquals(400000L, salaryResult.getBalanceMinor());
            assertEquals("INCOME", salaryResult.getAccountType());

            var equityResult = result.stream().filter(it -> equityId.equals(it.getAccountId())).findFirst().orElseThrow();
            assertEquals(150000L, equityResult.getBalanceMinor());
            assertEquals("EQUITY", equityResult.getAccountType());
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void emptyWhenNoAccounts() {
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(List.of());

            var result = service.getAllAccountBalances(userA, LocalDate.now());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Monthly expense summaries")
    class MonthlyExpenseSummaries {

        private final YearMonth jan2026 = YearMonth.of(2026, 1);
        private final YearMonth feb2026 = YearMonth.of(2026, 2);
        private final LocalDate janStart = LocalDate.of(2026, 1, 1);
        private final LocalDate janEnd = LocalDate.of(2026, 2, 1);
        private final LocalDate febStart = LocalDate.of(2026, 2, 1);
        private final LocalDate febEnd = LocalDate.of(2026, 3, 1);

        private final UUID foodTagId = UUID.randomUUID();
        private final UUID electricityTagId = UUID.randomUUID();

        @Test
        @DisplayName("expense totals correct per month in individual mode")
        void expenseTotalsPerMonthIndividualMode() {
            when(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                List.of(
                    new MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 8500L),
                    new MonthlyExpenseProjection(utilitiesId, "Utilities", electricityTagId, "Electricity", "EUR", 4500L)
                )
            );

            var result = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null);

            assertEquals(2, result.size());
            var groceriesResult = result.stream()
                .filter(it -> groceriesId.equals(it.getExpenseAccountId()))
                .findFirst()
                .orElseThrow();
            assertEquals("Groceries", groceriesResult.getExpenseAccountName());
            assertEquals("Food", groceriesResult.getCategoryTagName());
            assertEquals(8500L, groceriesResult.getNetMinor());

            var utilitiesResult = result.stream()
                .filter(it -> utilitiesId.equals(it.getExpenseAccountId()))
                .findFirst()
                .orElseThrow();
            assertEquals(4500L, utilitiesResult.getNetMinor());
        }

        @Test
        @DisplayName("different months return different totals")
        void differentMonthsReturnDifferentTotals() {
            when(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                List.of(new MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 8500L))
            );
            when(ledgerSplitRepository.monthlyExpensesByUser(1L, febStart, febEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                List.of(new MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 6000L))
            );

            var janResult = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null);
            var febResult = service.getMonthlyExpenses(userA, feb2026, "INDIVIDUAL", null);

            assertEquals(8500L, janResult.get(0).getNetMinor());
            assertEquals(6000L, febResult.get(0).getNetMinor());
        }

        @Test
        @DisplayName("expense totals in household mode use household query")
        void expenseTotalsHouseholdMode() {
            UUID householdId = UUID.randomUUID();
            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true);
            when(
                ledgerSplitRepository.monthlyExpensesByHousehold(
                    householdId,
                    janStart,
                    janEnd,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                )
            ).thenReturn(
                List.of(
                    new MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 5000L),
                    new MonthlyExpenseProjection(utilitiesId, "Utilities", electricityTagId, "Electricity", "EUR", 3000L)
                )
            );

            var result = service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", householdId);

            assertEquals(2, result.size());
            assertEquals(5000L, result.get(0).getNetMinor());
            assertEquals(3000L, result.get(1).getNetMinor());
        }

        @Test
        @DisplayName("household mode requires householdId")
        void householdModeRequiresHouseholdId() {
            BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", null)
            );
            assertTrue(ex.getMessage().contains("householdId is required"));
        }

        @Test
        @DisplayName("household mode returns 403 for non-member")
        void householdModeReturns403ForNonMember() {
            UUID householdId = UUID.randomUUID();
            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(false);

            ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", householdId)
            );
            assertTrue(ex.getMessage().contains("Not an active member"));
        }

        @Test
        @DisplayName("household mode succeeds for active member")
        void householdModeSucceedsForActiveMember() {
            UUID householdId = UUID.randomUUID();
            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true);
            when(
                ledgerSplitRepository.monthlyExpensesByHousehold(
                    householdId,
                    janStart,
                    janEnd,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                )
            ).thenReturn(
                List.of(new MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 7000L))
            );

            var result = service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", householdId);

            assertEquals(1, result.size());
            assertEquals(7000L, result.get(0).getNetMinor());
        }

        @Test
        @DisplayName("invalid mode is rejected")
        void invalidModeRejected() {
            BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.getMonthlyExpenses(userA, jan2026, "INVALID", null)
            );
            assertTrue(ex.getMessage().contains("Invalid mode"));
        }

        @Test
        @DisplayName("expense summary groups by account and category tag")
        void expenseSummaryGroupsByAccountAndCategory() {
            UUID beveragesTagId = UUID.randomUUID();
            when(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                List.of(
                    new MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 15000L),
                    new MonthlyExpenseProjection(groceriesId, "Groceries", beveragesTagId, "Beverages", "EUR", 8000L),
                    new MonthlyExpenseProjection(utilitiesId, "Utilities", electricityTagId, "Electricity", "EUR", 4500L),
                    new MonthlyExpenseProjection(utilitiesId, "Utilities", null, null, "EUR", 2000L)
                )
            );

            var result = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null);

            assertEquals(4, result.size());
            assertEquals(15000L, result.get(0).getNetMinor());
            assertEquals(8000L, result.get(1).getNetMinor());
            assertEquals(4500L, result.get(2).getNetMinor());
            assertEquals(2000L, result.get(3).getNetMinor());
            assertNull(result.get(3).getCategoryTagId());
            assertNull(result.get(3).getCategoryTagName());
        }

        @Test
        @DisplayName("returns empty list when no expenses in period")
        void emptyWhenNoExpenses() {
            when(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                List.of()
            );

            var result = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Balance timeseries")
    class BalanceTimeseries {

        @Test
        @DisplayName("should build cumulative daily timeseries for ASSET account")
        void cumulativeTimeseriesForAsset() {
            LocalDate dateFrom = LocalDate.of(2026, 2, 1);
            LocalDate dateTo = LocalDate.of(2026, 2, 3);

            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking));
            when(
                ledgerSplitRepository.computeBalance(
                    checkingId,
                    LocalDate.of(2026, 1, 31),
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                )
            ).thenReturn(100000L);
            when(
                ledgerSplitRepository.dailyNetByAccount(
                    checkingId,
                    dateFrom,
                    dateTo,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                )
            ).thenReturn(
                List.of(
                    new DailyNetProjection(LocalDate.of(2026, 2, 1), 200000L),
                    new DailyNetProjection(LocalDate.of(2026, 2, 3), -5000L)
                )
            );

            var result = service.getBalanceTimeseries(checkingId, dateFrom, dateTo, userA);

            assertEquals(checkingId, result.getAccountId());
            assertEquals("Checking", result.getAccountName());
            assertEquals("ASSET", result.getAccountType());
            assertEquals(3, result.getPoints().size());

            assertEquals(300000L, result.getPoints().get(0).getBalanceMinor());
            assertEquals(LocalDate.of(2026, 2, 1), result.getPoints().get(0).getDate());
            assertEquals(300000L, result.getPoints().get(1).getBalanceMinor());
            assertEquals(295000L, result.getPoints().get(2).getBalanceMinor());
        }

        @Test
        @DisplayName("should reject timeseries for non-owned account")
        void rejectTimeseriesForNonOwnedAccount() {
            LocalDate today = LocalDate.now();
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking));

            assertThrows(ForbiddenException.class, () -> service.getBalanceTimeseries(checkingId, today, today, userB));
        }

        @Test
        @DisplayName("should return 404 for non-existent account")
        void notFoundForMissingAccount() {
            UUID missingId = UUID.randomUUID();
            LocalDate today = LocalDate.now();
            when(accountRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.getBalanceTimeseries(missingId, today, today, userA));
        }

        @Test
        @DisplayName("should handle credit-normal account correctly in timeseries")
        void creditNormalTimeseriesForLiability() {
            LocalDate dateFrom = LocalDate.of(2026, 2, 1);
            LocalDate dateTo = LocalDate.of(2026, 2, 2);

            when(accountRepository.findById(mortgageId)).thenReturn(Optional.of(mortgage));
            when(
                ledgerSplitRepository.computeBalance(
                    mortgageId,
                    LocalDate.of(2026, 1, 31),
                    SplitSide.CREDIT,
                    SplitSide.DEBIT
                )
            ).thenReturn(500000L);
            when(
                ledgerSplitRepository.dailyNetByAccount(
                    mortgageId,
                    dateFrom,
                    dateTo,
                    SplitSide.CREDIT,
                    SplitSide.DEBIT
                )
            ).thenReturn(List.of(new DailyNetProjection(LocalDate.of(2026, 2, 1), -50000L)));

            var result = service.getBalanceTimeseries(mortgageId, dateFrom, dateTo, userA);

            assertEquals(2, result.getPoints().size());
            assertEquals(450000L, result.getPoints().get(0).getBalanceMinor());
            assertEquals(450000L, result.getPoints().get(1).getBalanceMinor());
        }

        @Test
        @DisplayName("single day timeseries should return one point")
        void singleDayTimeseries() {
            LocalDate date = LocalDate.of(2026, 2, 15);
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking));
            when(
                ledgerSplitRepository.computeBalance(
                    checkingId,
                    date.minusDays(1),
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                )
            ).thenReturn(100000L);
            when(
                ledgerSplitRepository.dailyNetByAccount(
                    checkingId,
                    date,
                    date,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT
                )
            ).thenReturn(List.of());

            var result = service.getBalanceTimeseries(checkingId, date, date, userA);
            assertEquals(1, result.getPoints().size());
            assertEquals(100000L, result.getPoints().get(0).getBalanceMinor());
            assertEquals(date, result.getPoints().get(0).getDate());
        }
    }

    private static User createUser(long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded");
        user.setEmail(email);
        return user;
    }
}
