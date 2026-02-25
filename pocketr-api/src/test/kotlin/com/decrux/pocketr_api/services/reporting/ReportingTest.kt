package com.decrux.pocketr_api.services.reporting

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.db.ledger.SplitSide
import com.decrux.pocketr_api.exceptions.DomainBadRequestException
import com.decrux.pocketr_api.exceptions.DomainForbiddenException
import com.decrux.pocketr_api.exceptions.DomainNotFoundException
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.DailyNetProjection
import com.decrux.pocketr_api.repositories.LedgerSplitRepository
import com.decrux.pocketr_api.repositories.MonthlyExpenseProjection
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for GenerateReportImpl (Section 12.3).
 *
 * Tests balance computation, monthly expense summaries, individual vs household
 * mode filtering, and timeseries generation with mocked repositories.
 *
 * User entity uses Long IDs (not UUID).
 */
@DisplayName("GenerateReportImpl — Reporting")
class ReportingTest {

    private lateinit var ledgerSplitRepository: LedgerSplitRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var manageHousehold: ManageHousehold
    private lateinit var service: GenerateReportImpl

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")

    private val userA = User(userId = 1L, password = "encoded", email = "alice@test.com")
    private val userB = User(userId = 2L, password = "encoded", email = "bob@test.com")

    private val checkingId = UUID.randomUUID()
    private val savingsId = UUID.randomUUID()
    private val groceriesId = UUID.randomUUID()
    private val utilitiesId = UUID.randomUUID()
    private val salaryId = UUID.randomUUID()
    private val mortgageId = UUID.randomUUID()
    private val equityId = UUID.randomUUID()

    private val checking = Account(id = checkingId, owner = userA, name = "Checking", type = AccountType.ASSET, currency = eur)
    private val savings = Account(id = savingsId, owner = userA, name = "Savings", type = AccountType.ASSET, currency = eur)
    private val groceries = Account(id = groceriesId, owner = userA, name = "Groceries", type = AccountType.EXPENSE, currency = eur)
    private val utilities = Account(id = utilitiesId, owner = userA, name = "Utilities", type = AccountType.EXPENSE, currency = eur)
    private val salary = Account(id = salaryId, owner = userA, name = "Salary", type = AccountType.INCOME, currency = eur)
    private val mortgage = Account(id = mortgageId, owner = userA, name = "Mortgage", type = AccountType.LIABILITY, currency = eur)
    private val equity = Account(id = equityId, owner = userA, name = "Opening Equity", type = AccountType.EQUITY, currency = eur)

    @BeforeEach
    fun setUp() {
        ledgerSplitRepository = mock(LedgerSplitRepository::class.java)
        accountRepository = mock(AccountRepository::class.java)
        manageHousehold = mock(ManageHousehold::class.java)
        service = GenerateReportImpl(ledgerSplitRepository, accountRepository, manageHousehold)
    }

    @Nested
    @DisplayName("Account balance summaries")
    inner class BalanceComputation {

        @Test
        @DisplayName("should compute correct balances for all account types")
        fun computeBalancesForAllAccountTypes() {
            val allAccounts = listOf(checking, savings, groceries, utilities, salary, mortgage, equity)
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(allAccounts)

            val asOf = LocalDate.of(2026, 2, 15)

            // ASSET (debit-normal): balance = DEBIT - CREDIT
            `when`(ledgerSplitRepository.computeBalance(checkingId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(190000L)
            `when`(ledgerSplitRepository.computeBalance(savingsId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(50000L)
            // EXPENSE (debit-normal)
            `when`(ledgerSplitRepository.computeBalance(groceriesId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(8500L)
            `when`(ledgerSplitRepository.computeBalance(utilitiesId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(4500L)
            // INCOME (credit-normal): balance = CREDIT - DEBIT
            `when`(ledgerSplitRepository.computeBalance(salaryId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)).thenReturn(400000L)
            // LIABILITY (credit-normal)
            `when`(ledgerSplitRepository.computeBalance(mortgageId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)).thenReturn(49950000L)
            // EQUITY (credit-normal)
            `when`(ledgerSplitRepository.computeBalance(equityId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)).thenReturn(150000L)

            val result = service.getAllAccountBalances(userA, asOf)

            assertEquals(7, result.size)

            val checkingResult = result.first { it.accountId == checkingId }
            assertEquals(190000L, checkingResult.balanceMinor)
            assertEquals("ASSET", checkingResult.accountType)
            assertEquals("EUR", checkingResult.currency)

            val mortgageResult = result.first { it.accountId == mortgageId }
            assertEquals(49950000L, mortgageResult.balanceMinor)
            assertEquals("LIABILITY", mortgageResult.accountType)

            val salaryResult = result.first { it.accountId == salaryId }
            assertEquals(400000L, salaryResult.balanceMinor)
            assertEquals("INCOME", salaryResult.accountType)

            val equityResult = result.first { it.accountId == equityId }
            assertEquals(150000L, equityResult.balanceMinor)
            assertEquals("EQUITY", equityResult.accountType)
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        fun emptyWhenNoAccounts() {
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(emptyList())

            val result = service.getAllAccountBalances(userA, LocalDate.now())
            assertTrue(result.isEmpty())
        }

    }

    @Nested
    @DisplayName("Monthly expense summaries")
    inner class MonthlyExpenseSummaries {

        private val jan2026 = YearMonth.of(2026, 1)
        private val feb2026 = YearMonth.of(2026, 2)
        private val janStart = LocalDate.of(2026, 1, 1)
        private val janEnd = LocalDate.of(2026, 2, 1)
        private val febStart = LocalDate.of(2026, 2, 1)
        private val febEnd = LocalDate.of(2026, 3, 1)

        private val foodTagId = UUID.randomUUID()
        private val electricityTagId = UUID.randomUUID()

        @Test
        @DisplayName("expense totals correct per month in individual mode")
        fun expenseTotalsPerMonthIndividualMode() {
            // January: Groceries/Food: 8500, Utilities/Electricity: 4500
            `when`(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                listOf(
                    MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 8500L),
                    MonthlyExpenseProjection(utilitiesId, "Utilities", electricityTagId, "Electricity", "EUR", 4500L),
                ),
            )

            val result = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null)

            assertEquals(2, result.size)
            val groceriesResult = result.first { it.expenseAccountId == groceriesId }
            assertEquals("Groceries", groceriesResult.expenseAccountName)
            assertEquals("Food", groceriesResult.categoryTagName)
            assertEquals(8500L, groceriesResult.netMinor)

            val utilitiesResult = result.first { it.expenseAccountId == utilitiesId }
            assertEquals(4500L, utilitiesResult.netMinor)
        }

        @Test
        @DisplayName("different months return different totals")
        fun differentMonthsReturnDifferentTotals() {
            // January has expenses
            `when`(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                listOf(MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 8500L)),
            )
            // February has different expenses
            `when`(ledgerSplitRepository.monthlyExpensesByUser(1L, febStart, febEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                listOf(MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 6000L)),
            )

            val janResult = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null)
            val febResult = service.getMonthlyExpenses(userA, feb2026, "INDIVIDUAL", null)

            assertEquals(8500L, janResult[0].netMinor)
            assertEquals(6000L, febResult[0].netMinor)
        }

        @Test
        @DisplayName("expense totals in household mode use household query")
        fun expenseTotalsHouseholdMode() {
            val householdId = UUID.randomUUID()
            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true)
            `when`(ledgerSplitRepository.monthlyExpensesByHousehold(householdId, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                listOf(
                    MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 5000L),
                    MonthlyExpenseProjection(utilitiesId, "Utilities", electricityTagId, "Electricity", "EUR", 3000L),
                ),
            )

            val result = service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", householdId)

            assertEquals(2, result.size)
            assertEquals(5000L, result[0].netMinor)
            assertEquals(3000L, result[1].netMinor)
        }

        @Test
        @DisplayName("household mode requires householdId")
        fun householdModeRequiresHouseholdId() {
            val ex = assertThrows(DomainBadRequestException::class.java) {
                service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", null)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("householdId is required"))
        }

        @Test
        @DisplayName("household mode returns 403 for non-member")
        fun householdModeReturns403ForNonMember() {
            val householdId = UUID.randomUUID()
            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(false)

            val ex = assertThrows(DomainForbiddenException::class.java) {
                service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", householdId)
            }
            assertEquals(403, ex.status.value())
            assertTrue(ex.message!!.contains("Not an active member"))
        }

        @Test
        @DisplayName("household mode succeeds for active member")
        fun householdModeSucceedsForActiveMember() {
            val householdId = UUID.randomUUID()
            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true)
            `when`(ledgerSplitRepository.monthlyExpensesByHousehold(householdId, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                listOf(MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 7000L)),
            )

            val result = service.getMonthlyExpenses(userA, jan2026, "HOUSEHOLD", householdId)

            assertEquals(1, result.size)
            assertEquals(7000L, result[0].netMinor)
        }

        @Test
        @DisplayName("invalid mode is rejected")
        fun invalidModeRejected() {
            val ex = assertThrows(DomainBadRequestException::class.java) {
                service.getMonthlyExpenses(userA, jan2026, "INVALID", null)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Invalid mode"))
        }

        @Test
        @DisplayName("expense summary groups by account and category tag")
        fun expenseSummaryGroupsByAccountAndCategory() {
            val beveragesTagId = UUID.randomUUID()
            `when`(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(
                listOf(
                    MonthlyExpenseProjection(groceriesId, "Groceries", foodTagId, "Food", "EUR", 15000L),
                    MonthlyExpenseProjection(groceriesId, "Groceries", beveragesTagId, "Beverages", "EUR", 8000L),
                    MonthlyExpenseProjection(utilitiesId, "Utilities", electricityTagId, "Electricity", "EUR", 4500L),
                    MonthlyExpenseProjection(utilitiesId, "Utilities", null, null, "EUR", 2000L),
                ),
            )

            val result = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null)

            assertEquals(4, result.size)
            assertEquals(15000L, result[0].netMinor)  // Groceries/Food
            assertEquals(8000L, result[1].netMinor)   // Groceries/Beverages
            assertEquals(4500L, result[2].netMinor)   // Utilities/Electricity
            assertEquals(2000L, result[3].netMinor)   // Utilities/(null)
            assertNull(result[3].categoryTagId)
            assertNull(result[3].categoryTagName)
        }

        @Test
        @DisplayName("returns empty list when no expenses in period")
        fun emptyWhenNoExpenses() {
            `when`(ledgerSplitRepository.monthlyExpensesByUser(1L, janStart, janEnd, SplitSide.DEBIT, SplitSide.CREDIT)).thenReturn(emptyList())

            val result = service.getMonthlyExpenses(userA, jan2026, "INDIVIDUAL", null)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Balance timeseries")
    inner class BalanceTimeseries {

        @Test
        @DisplayName("should build cumulative daily timeseries for ASSET account")
        fun cumulativeTimeseriesForAsset() {
            val dateFrom = LocalDate.of(2026, 2, 1)
            val dateTo = LocalDate.of(2026, 2, 3)

            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking))
            // Opening balance (everything before Feb 1)
            `when`(
                ledgerSplitRepository.computeBalance(
                    checkingId, LocalDate.of(2026, 1, 31), SplitSide.DEBIT, SplitSide.CREDIT,
                )
            ).thenReturn(100000L)
            // Daily changes
            `when`(
                ledgerSplitRepository.dailyNetByAccount(
                    checkingId, dateFrom, dateTo, SplitSide.DEBIT, SplitSide.CREDIT,
                )
            ).thenReturn(
                listOf(
                    DailyNetProjection(LocalDate.of(2026, 2, 1), 200000L),  // salary
                    DailyNetProjection(LocalDate.of(2026, 2, 3), -5000L),   // expense
                ),
            )

            val result = service.getBalanceTimeseries(checkingId, dateFrom, dateTo, userA)

            assertEquals(checkingId, result.accountId)
            assertEquals("Checking", result.accountName)
            assertEquals("ASSET", result.accountType)
            assertEquals(3, result.points.size)

            // Feb 1: 100000 + 200000 = 300000
            assertEquals(300000L, result.points[0].balanceMinor)
            assertEquals(LocalDate.of(2026, 2, 1), result.points[0].date)
            // Feb 2: no change → 300000
            assertEquals(300000L, result.points[1].balanceMinor)
            // Feb 3: 300000 - 5000 = 295000
            assertEquals(295000L, result.points[2].balanceMinor)
        }

        @Test
        @DisplayName("should reject timeseries for non-owned account")
        fun rejectTimeseriesForNonOwnedAccount() {
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking))

            val ex = assertThrows(DomainForbiddenException::class.java) {
                service.getBalanceTimeseries(checkingId, LocalDate.now(), LocalDate.now(), userB)
            }
            assertEquals(403, ex.status.value())
        }

        @Test
        @DisplayName("should return 404 for non-existent account")
        fun notFoundForMissingAccount() {
            val missingId = UUID.randomUUID()
            `when`(accountRepository.findById(missingId)).thenReturn(Optional.empty())

            val ex = assertThrows(DomainNotFoundException::class.java) {
                service.getBalanceTimeseries(missingId, LocalDate.now(), LocalDate.now(), userA)
            }
            assertEquals(404, ex.status.value())
        }

        @Test
        @DisplayName("should handle credit-normal account correctly in timeseries")
        fun creditNormalTimeseriesForLiability() {
            val dateFrom = LocalDate.of(2026, 2, 1)
            val dateTo = LocalDate.of(2026, 2, 2)

            `when`(accountRepository.findById(mortgageId)).thenReturn(Optional.of(mortgage))
            `when`(
                ledgerSplitRepository.computeBalance(
                    mortgageId, LocalDate.of(2026, 1, 31), SplitSide.CREDIT, SplitSide.DEBIT,
                )
            ).thenReturn(500000L)
            `when`(
                ledgerSplitRepository.dailyNetByAccount(
                    mortgageId, dateFrom, dateTo, SplitSide.CREDIT, SplitSide.DEBIT,
                )
            ).thenReturn(
                listOf(DailyNetProjection(LocalDate.of(2026, 2, 1), -50000L)),  // payment reduces liability
            )

            val result = service.getBalanceTimeseries(mortgageId, dateFrom, dateTo, userA)

            assertEquals(2, result.points.size)
            assertEquals(450000L, result.points[0].balanceMinor)  // 500000 - 50000
            assertEquals(450000L, result.points[1].balanceMinor)  // no change on Feb 2
        }

        @Test
        @DisplayName("single day timeseries should return one point")
        fun singleDayTimeseries() {
            val date = LocalDate.of(2026, 2, 15)
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking))
            `when`(
                ledgerSplitRepository.computeBalance(
                    checkingId, date.minusDays(1), SplitSide.DEBIT, SplitSide.CREDIT,
                )
            ).thenReturn(100000L)
            `when`(
                ledgerSplitRepository.dailyNetByAccount(
                    checkingId, date, date, SplitSide.DEBIT, SplitSide.CREDIT,
                )
            ).thenReturn(emptyList())

            val result = service.getBalanceTimeseries(checkingId, date, date, userA)
            assertEquals(1, result.points.size)
            assertEquals(100000L, result.points[0].balanceMinor)
            assertEquals(date, result.points[0].date)
        }
    }
}
