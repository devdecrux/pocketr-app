package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.exceptions.ForbiddenException
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.CurrencyExchangeRateRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.repositories.LedgerSplitRepository
import com.decrux.pocketr.api.repositories.LedgerTxnRepository
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@UsePostgresDb
@TestPropertySource(properties = ["ledger.accounts.snapshot.balance.enabled=true"])
@DisplayName("ManageLedger current balance integration")
class ManageLedgerCurrentBalanceIntegrationTest
    @Autowired
    constructor(
        private val manageLedger: ManageLedger,
        private val userRepository: UserRepository,
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val currencyExchangeRateRepository: CurrencyExchangeRateRepository,
        private val ledgerTxnRepository: LedgerTxnRepository,
        private val ledgerSplitRepository: LedgerSplitRepository,
        private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
        private val currentAccountBalanceMonitor: CurrentAccountBalanceMonitor,
    ) {
        private lateinit var eur: Currency
        private lateinit var usd: Currency

        @BeforeEach
        fun cleanState() {
            accountCurrentBalanceRepository.deleteAll()
            ledgerTxnRepository.deleteAll()
            currencyExchangeRateRepository.deleteAll()
            accountRepository.deleteAll()
            userRepository.deleteAll()

            eur =
                currencyRepository.findById("EUR").orElseGet {
                    currencyRepository.save(
                        Currency(code = "EUR", minorUnit = 2, name = "Euro", symbol = "€"),
                    )
                }
            usd =
                currencyRepository.findById("USD").orElseGet {
                    currencyRepository.save(
                        Currency(code = "USD", minorUnit = 2, name = "US Dollar", symbol = "$"),
                    )
                }

            currentAccountBalanceMonitor.logIntegrityStatusOnStartup()
        }

        @Test
        @DisplayName("posting updates projection and balances match computed baseline")
        fun postingUpdatesProjectionAndMatchesComputed() {
            val user = persistUser("integration-balance")
            val cash = persistAccount(user, "Cash", AccountType.ASSET)
            val expense = persistAccount(user, "Expense", AccountType.EXPENSE)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Lunch",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = requireNotNull(cash.id), side = "CREDIT", amountMinor = 1_200),
                                CreateSplitDto(accountId = requireNotNull(expense.id), side = "DEBIT", amountMinor = 1_200),
                            ),
                    ),
                creator = user,
            )

            val projectionById =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(requireNotNull(cash.id), requireNotNull(expense.id)))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
            assertEquals(-1_200L, projectionById.getValue(requireNotNull(cash.id)))
            assertEquals(1_200L, projectionById.getValue(requireNotNull(expense.id)))

            val computedById =
                ledgerSplitRepository
                    .computeRawBalancesByAccountIds(
                        listOf(requireNotNull(cash.id), requireNotNull(expense.id)),
                        today,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    ).associate { it.accountId to it.rawBalance }
            assertEquals(computedById, projectionById)

            val cashBalance = manageLedger.getAccountBalance(requireNotNull(cash.id), today, user, null)
            val expenseBalance = manageLedger.getAccountBalance(requireNotNull(expense.id), today, user, null)
            assertEquals(-1_200L, cashBalance.balanceMinor)
            assertEquals(1_200L, expenseBalance.balanceMinor)
        }

        @Test
        @DisplayName("deleting reverses projection and cascades ledger splits")
        fun deletingReversesProjectionAndCascadesLedgerSplits() {
            val user = persistUser("integration-delete")
            val cash = persistAccount(user, "Cash Delete", AccountType.ASSET)
            val expense = persistAccount(user, "Expense Delete", AccountType.EXPENSE)
            val cashId = requireNotNull(cash.id)
            val expenseId = requireNotNull(expense.id)
            val today = LocalDate.now()

            val txn =
                manageLedger.createTransaction(
                    dto =
                        CreateTransactionDto(
                            txnDate = today,
                            currency = "EUR",
                            description = "Delete reversal check",
                            splits =
                                listOf(
                                    CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 1_200),
                                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1_200),
                                ),
                        ),
                    creator = user,
                )

            assertEquals(1L, ledgerTxnRepository.count())
            assertEquals(2L, ledgerSplitRepository.count())

            manageLedger.deleteTransaction(txn.id, user)

            assertEquals(0L, ledgerTxnRepository.count())
            assertEquals(0L, ledgerSplitRepository.count())

            val projectionById =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(cashId, expenseId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
            assertEquals(0L, projectionById.getValue(cashId))
            assertEquals(0L, projectionById.getValue(expenseId))
            assertEquals(0L, manageLedger.getAccountBalance(cashId, today, user, null).balanceMinor)
            assertEquals(0L, manageLedger.getAccountBalance(expenseId, today, user, null).balanceMinor)
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("deleting is rejected when a referenced account is archived")
        fun deletingIsRejectedForArchivedAccountHistory() {
            val user = persistUser("integration-immutable-history")
            val cash = persistAccount(user, "Archived Cash", AccountType.ASSET)
            val expense = persistAccount(user, "Historical Expense", AccountType.EXPENSE)
            val cashId = requireNotNull(cash.id)
            val expenseId = requireNotNull(expense.id)
            val txn =
                manageLedger.createTransaction(
                    dto =
                        CreateTransactionDto(
                            txnDate = LocalDate.now(),
                            currency = "EUR",
                            description = "Keep this history",
                            splits =
                                listOf(
                                    CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 1_200),
                                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1_200),
                                ),
                        ),
                    creator = user,
                )
            cash.archive(Instant.parse("2026-07-12T10:00:00Z"))
            accountRepository.saveAndFlush(cash)

            val exception =
                assertThrows(BadRequestException::class.java) {
                    manageLedger.deleteTransaction(txn.id, user)
                }

            assertEquals("Transactions involving archived accounts cannot be deleted", exception.message)
            assertEquals(1L, ledgerTxnRepository.count())
            assertEquals(2L, ledgerSplitRepository.count())
            val projectionById =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(cashId, expenseId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
            assertEquals(-1_200L, projectionById.getValue(cashId))
            assertEquals(1_200L, projectionById.getValue(expenseId))
        }

        @Test
        @DisplayName("today request prefers snapshot balance over computed balance")
        @Transactional
        fun todayRequestPrefersSnapshotBalance() {
            val user = persistUser("integration-snapshot-balance")
            val cash = persistAccount(user, "Cash Fast", AccountType.ASSET)
            val expense = persistAccount(user, "Expense Fast", AccountType.EXPENSE)
            val cashId = requireNotNull(cash.id)
            val expenseId = requireNotNull(expense.id)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Fast-path preference check",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 1_200),
                                CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1_200),
                            ),
                    ),
                creator = user,
            )

            accountCurrentBalanceRepository.addDelta(cashId, 5_000L)

            val todayBalance = manageLedger.getAccountBalance(cashId, today, user, null)
            assertEquals(3_800L, todayBalance.balanceMinor)
        }

        @Test
        @DisplayName("today multi-account request prefers snapshot balance over computed balance")
        @Transactional
        fun todayMultiAccountRequestPrefersSnapshotBalance() {
            val user = persistUser("integration-snapshot-balance-list")
            val cash = persistAccount(user, "Cash Fast List", AccountType.ASSET)
            val expense = persistAccount(user, "Expense Fast List", AccountType.EXPENSE)
            val cashId = requireNotNull(cash.id)
            val expenseId = requireNotNull(expense.id)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Fast-path list preference check",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 1_200),
                                CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1_200),
                            ),
                    ),
                creator = user,
            )

            accountCurrentBalanceRepository.addDelta(cashId, 5_000L)

            val balances =
                manageLedger
                    .getAccountBalances(listOf(cashId, expenseId), today, user, null)
                    .associateBy { it.accountId }
            assertEquals(3_800L, balances.getValue(cashId).balanceMinor)
            assertEquals(1_200L, balances.getValue(expenseId).balanceMinor)
        }

        @Test
        @DisplayName("historical asOf stays on computed behavior")
        fun historicalAsOfUsesComputedBehavior() {
            val user = persistUser("integration-historical")
            val cash = persistAccount(user, "Cash Hist", AccountType.ASSET)
            val expense = persistAccount(user, "Expense Hist", AccountType.EXPENSE)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Historical check",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = requireNotNull(cash.id), side = "CREDIT", amountMinor = 1_000),
                                CreateSplitDto(accountId = requireNotNull(expense.id), side = "DEBIT", amountMinor = 1_000),
                            ),
                    ),
                creator = user,
            )

            val yesterday = today.minusDays(1)
            val historical = manageLedger.getAccountBalance(requireNotNull(cash.id), yesterday, user, null)
            assertEquals(0L, historical.balanceMinor)
        }

        @Test
        @Transactional
        @DisplayName("archived accounts remain readable in transaction history and balances")
        fun archivedAccountHistoryRemainsReadable() {
            val user = persistUser("integration-archived-history")
            val cash = persistAccount(user, "Archived Cash", AccountType.ASSET)
            val expense = persistAccount(user, "Archived Expense", AccountType.EXPENSE)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Preserved history",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = requireNotNull(cash.id), side = "CREDIT", amountMinor = 1_000),
                                CreateSplitDto(accountId = requireNotNull(expense.id), side = "DEBIT", amountMinor = 1_000),
                            ),
                    ),
                creator = user,
            )
            cash.archive(Instant.parse("2026-07-12T10:00:00Z"))
            accountRepository.saveAndFlush(cash)

            val transactions =
                manageLedger.listTransactions(user, TransactionListFilter(mode = "INDIVIDUAL"), 0, 20)
            val balance = manageLedger.getAccountBalance(requireNotNull(cash.id), today, user, null)
            val archivedSplit =
                transactions.content
                    .single()
                    .splits
                    .single { it.accountId == cash.id }

            assertEquals(1, transactions.totalElements)
            assertEquals(cash.name, archivedSplit.accountName)
            assertEquals(-1_000L, balance.balanceMinor)
        }

        @Test
        @DisplayName("normal posting keeps integrity mismatch count at zero")
        fun normalPostingKeepsIntegrityMismatchCountZero() {
            val user = persistUser("integration-reconcile-ok")
            val cash = persistAccount(user, "Cash Reconcile", AccountType.ASSET)
            val expense = persistAccount(user, "Expense Reconcile", AccountType.EXPENSE)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Reconciliation happy path",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = requireNotNull(cash.id), side = "CREDIT", amountMinor = 1_200),
                                CreateSplitDto(accountId = requireNotNull(expense.id), side = "DEBIT", amountMinor = 1_200),
                            ),
                    ),
                creator = user,
            )

            currentAccountBalanceMonitor.logIntegrityStatusOnStartup()
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("integrity mismatch disables snapshot only for mismatched account")
        @Transactional
        fun integrityMismatchDisablesSnapshotBalance() {
            val user = persistUser("integration-snapshot-balance-gate")
            val cash = persistAccount(user, "Cash Gate", AccountType.ASSET)
            val savings = persistAccount(user, "Savings Gate", AccountType.ASSET)
            val cashId = requireNotNull(cash.id)
            val savingsId = requireNotNull(savings.id)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Fast-path gate check",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 1_200),
                                CreateSplitDto(accountId = savingsId, side = "DEBIT", amountMinor = 1_200),
                            ),
                    ),
                creator = user,
            )

            accountCurrentBalanceRepository.addDelta(cashId, 5_000L)
            currentAccountBalanceMonitor.logIntegrityStatusOnStartup()

            // Tamper a healthy account after the integrity check to verify it still uses snapshot reads.
            accountCurrentBalanceRepository.addDelta(savingsId, 700L)

            val cashBalance = manageLedger.getAccountBalance(cashId, today, user, null)
            val savingsBalance = manageLedger.getAccountBalance(savingsId, today, user, null)
            assertEquals(-1_200L, cashBalance.balanceMinor)
            assertEquals(1_900L, savingsBalance.balanceMinor)
        }

        @Test
        @DisplayName("same-account multi-split deltas are computed correctly")
        fun sameAccountMultiSplitDeltasAreComputed() {
            val user = persistUser("integration-multi-split")
            val cash = persistAccount(user, "Cash Multi", AccountType.ASSET)
            val groceries = persistAccount(user, "Groceries Multi", AccountType.EXPENSE)
            val fuel = persistAccount(user, "Fuel Multi", AccountType.EXPENSE)
            val cashId = requireNotNull(cash.id)
            val groceriesId = requireNotNull(groceries.id)
            val fuelId = requireNotNull(fuel.id)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Multi-split aggregation",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 700),
                                CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = 300),
                                CreateSplitDto(accountId = groceriesId, side = "DEBIT", amountMinor = 400),
                                CreateSplitDto(accountId = fuelId, side = "DEBIT", amountMinor = 600),
                            ),
                    ),
                creator = user,
            )

            val projectionById =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(cashId, groceriesId, fuelId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
            assertEquals(-1_000L, projectionById.getValue(cashId))
            assertEquals(400L, projectionById.getValue(groceriesId))
            assertEquals(600L, projectionById.getValue(fuelId))
        }

        @Test
        @DisplayName("credit-normal account balance is sign-normalized from raw projection")
        fun creditNormalAccountBalanceIsSignNormalized() {
            val user = persistUser("integration-credit-normal")
            val cash = persistAccount(user, "Cash Credit Normal", AccountType.ASSET)
            val liability = persistAccount(user, "Liability Credit Normal", AccountType.LIABILITY)
            val cashId = requireNotNull(cash.id)
            val liabilityId = requireNotNull(liability.id)
            val today = LocalDate.now()

            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = today,
                        currency = "EUR",
                        description = "Liability increase",
                        splits =
                            listOf(
                                CreateSplitDto(accountId = cashId, side = "DEBIT", amountMinor = 1_500),
                                CreateSplitDto(accountId = liabilityId, side = "CREDIT", amountMinor = 1_500),
                            ),
                    ),
                creator = user,
            )

            val liabilityRaw =
                accountCurrentBalanceRepository
                    .findById(liabilityId)
                    .orElseThrow()
                    .rawBalanceMinor
            assertEquals(-1_500L, liabilityRaw)

            val liabilityBalance = manageLedger.getAccountBalance(liabilityId, today, user, null)
            assertEquals(1_500L, liabilityBalance.balanceMinor)
        }

        @Test
        @DisplayName("spendingOnly includes expenses and debt payments only")
        fun spendingOnlyIncludesExpensesAndDebtPaymentsOnly() {
            val user = persistUser("integration-spending-filter")
            val cash = persistAccount(user, "Filter Cash", AccountType.ASSET)
            val savings = persistAccount(user, "Filter Savings", AccountType.ASSET)
            val expense = persistAccount(user, "Filter Expense", AccountType.EXPENSE)
            val income = persistAccount(user, "Filter Income", AccountType.INCOME)
            val liability = persistAccount(user, "Filter Liability", AccountType.LIABILITY)
            val equity = persistAccount(user, "Filter Equity", AccountType.EQUITY)
            val cashId = requireNotNull(cash.id)
            val savingsId = requireNotNull(savings.id)
            val expenseId = requireNotNull(expense.id)
            val incomeId = requireNotNull(income.id)
            val liabilityId = requireNotNull(liability.id)
            val equityId = requireNotNull(equity.id)
            val today = LocalDate.now()

            postTxn(user, today, "Expense", cashId to "CREDIT", expenseId to "DEBIT")
            postTxn(user, today, "Debt payment", cashId to "CREDIT", liabilityId to "DEBIT")
            postTxn(user, today, "Income", cashId to "DEBIT", incomeId to "CREDIT")
            postTxn(user, today, "Transfer", savingsId to "DEBIT", cashId to "CREDIT")
            postTxn(user, today, "Opening balance", cashId to "DEBIT", equityId to "CREDIT")
            postTxn(user, today, "Opening debt", equityId to "DEBIT", liabilityId to "CREDIT")

            val result =
                manageLedger.listTransactions(
                    user = user,
                    filter =
                        TransactionListFilter(
                            mode = "INDIVIDUAL",
                            householdId = null,
                            dateFrom = null,
                            dateTo = null,
                            accountId = null,
                            categoryId = null,
                            spendingOnly = true,
                        ),
                    page = 0,
                    size = 10,
                )

            assertEquals(setOf("Expense", "Debt payment"), result.content.map { it.description }.toSet())
        }

        @Test
        @DisplayName("projection failure rolls back persisted ledger rows")
        fun projectionFailureRollsBackLedgerRows() {
            val user = persistUser("integration-rollback")
            val accountA = persistAccount(user, "Overflow A", AccountType.ASSET)
            val accountB = persistAccount(user, "Overflow B", AccountType.ASSET)
            val accountAId = requireNotNull(accountA.id)
            val accountBId = requireNotNull(accountB.id)
            val beforeTxnCount = ledgerTxnRepository.count()
            val beforeSplitCount = ledgerSplitRepository.count()
            val hugeAmount = Long.MAX_VALUE

            assertThrows(ArithmeticException::class.java) {
                manageLedger.createTransaction(
                    dto =
                        CreateTransactionDto(
                            txnDate = LocalDate.now(),
                            currency = "EUR",
                            description = "Overflow projection rollback",
                            splits =
                                listOf(
                                    CreateSplitDto(accountId = accountAId, side = "DEBIT", amountMinor = hugeAmount),
                                    CreateSplitDto(accountId = accountAId, side = "DEBIT", amountMinor = hugeAmount),
                                    CreateSplitDto(accountId = accountBId, side = "CREDIT", amountMinor = hugeAmount),
                                    CreateSplitDto(accountId = accountBId, side = "CREDIT", amountMinor = hugeAmount),
                                ),
                        ),
                    creator = user,
                )
            }

            assertEquals(beforeTxnCount, ledgerTxnRepository.count())
            assertEquals(beforeSplitCount, ledgerSplitRepository.count())
            assertEquals(
                emptyMap<UUID, Long>(),
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(accountAId, accountBId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor },
            )
        }

        @Test
        @DisplayName("cross-currency posting and deletion use persisted account-currency amounts")
        fun crossCurrencyPostingAndDeletionUsePersistedAmounts() {
            val user = persistUser("integration-cross-currency")
            val usdCash = persistAccount(user, "USD Cash", AccountType.ASSET, usd)
            val eurExpense = persistAccount(user, "EUR Expense", AccountType.EXPENSE, eur)
            val usdCashId = requireNotNull(usdCash.id)
            val eurExpenseId = requireNotNull(eurExpense.id)
            val today = LocalDate.now()
            persistRate("EUR", "USD", "1.20")

            val txn =
                manageLedger.createTransaction(
                    dto =
                        CreateTransactionDto(
                            txnDate = today,
                            currency = "EUR",
                            description = "Cross currency expense",
                            splits =
                                listOf(
                                    CreateSplitDto(accountId = usdCashId, side = "CREDIT", amountMinor = 1_000),
                                    CreateSplitDto(accountId = eurExpenseId, side = "DEBIT", amountMinor = 1_000),
                                ),
                        ),
                    creator = user,
                )

            val splitByAccount = txn.splits.associateBy { it.accountId }
            assertEquals(1_200L, splitByAccount.getValue(usdCashId).amountMinor)
            assertEquals("USD", splitByAccount.getValue(usdCashId).accountCurrency)
            assertEquals("1.2", splitByAccount.getValue(usdCashId).exchangeRate)
            assertEquals(1_000L, splitByAccount.getValue(eurExpenseId).amountMinor)

            val projectionById =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(usdCashId, eurExpenseId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
            assertEquals(-1_200L, projectionById.getValue(usdCashId))
            assertEquals(1_000L, projectionById.getValue(eurExpenseId))
            assertEquals(-1_200L, manageLedger.getAccountBalance(usdCashId, today, user, null).balanceMinor)
            assertEquals(1_000L, manageLedger.getAccountBalance(eurExpenseId, today, user, null).balanceMinor)

            manageLedger.deleteTransaction(txn.id, user)

            val afterDeleteById =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(usdCashId, eurExpenseId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
            assertEquals(0L, afterDeleteById.getValue(usdCashId))
            assertEquals(0L, afterDeleteById.getValue(eurExpenseId))
            assertEquals(0L, ledgerTxnRepository.count())
            assertEquals(0L, ledgerSplitRepository.count())
        }

        @Test
        @DisplayName("missing cross-currency rate rolls back without partial writes")
        fun missingCrossCurrencyRateRollsBackWithoutPartialWrites() {
            val user = persistUser("integration-cross-currency-rollback")
            val usdCash = persistAccount(user, "USD Rollback Cash", AccountType.ASSET, usd)
            val eurExpense = persistAccount(user, "EUR Rollback Expense", AccountType.EXPENSE, eur)
            val usdCashId = requireNotNull(usdCash.id)
            val eurExpenseId = requireNotNull(eurExpense.id)
            val beforeTxnCount = ledgerTxnRepository.count()
            val beforeSplitCount = ledgerSplitRepository.count()

            assertThrows(BadRequestException::class.java) {
                manageLedger.createTransaction(
                    dto =
                        CreateTransactionDto(
                            txnDate = LocalDate.now(),
                            currency = "EUR",
                            description = "Missing rate rollback",
                            splits =
                                listOf(
                                    CreateSplitDto(accountId = usdCashId, side = "CREDIT", amountMinor = 1_000),
                                    CreateSplitDto(accountId = eurExpenseId, side = "DEBIT", amountMinor = 1_000),
                                ),
                        ),
                    creator = user,
                )
            }

            assertEquals(beforeTxnCount, ledgerTxnRepository.count())
            assertEquals(beforeSplitCount, ledgerSplitRepository.count())
            assertEquals(
                emptyMap<UUID, Long>(),
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(listOf(usdCashId, eurExpenseId))
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor },
            )
        }

        @Test
        @DisplayName("editing amount and description reapplies projection and keeps balances consistent")
        fun editingAmountUpdatesProjectionAndBalances() {
            val user = persistUser("integration-edit-amount")
            val cashId = requireNotNull(persistAccount(user, "Edit Cash", AccountType.ASSET).id)
            val expenseId = requireNotNull(persistAccount(user, "Edit Expense", AccountType.EXPENSE).id)
            val today = LocalDate.now()
            val created = manageLedger.createTransaction(editDto(cashId, expenseId, 1_200, "Lunch"), user)

            val edited = manageLedger.updateTransaction(created.id, editDto(cashId, expenseId, 2_500, "  Dinner  "), user)

            assertEquals(created.id, edited.id)
            assertEquals("Dinner", edited.description)
            assertEquals(1L, ledgerTxnRepository.count())
            assertEquals(2L, ledgerSplitRepository.count())
            assertEquals(setOf(2_500L), edited.splits.map { it.amountMinor }.toSet())
            assertEquals(mapOf(cashId to -2_500L, expenseId to 2_500L), projection(cashId, expenseId))
            assertEquals(-2_500L, manageLedger.getAccountBalance(cashId, today, user, null).balanceMinor)
            assertEquals(2_500L, manageLedger.getAccountBalance(expenseId, today, user, null).balanceMinor)
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("moving a split to another account reverses the old account and applies the new one")
        fun editingMovesSplitBetweenAccounts() {
            val user = persistUser("integration-edit-move")
            val cashId = requireNotNull(persistAccount(user, "Move Cash", AccountType.ASSET).id)
            val cardId = requireNotNull(persistAccount(user, "Move Card", AccountType.ASSET).id)
            val expenseId = requireNotNull(persistAccount(user, "Move Expense", AccountType.EXPENSE).id)
            val created = manageLedger.createTransaction(editDto(cashId, expenseId, 1_000, "Coffee"), user)

            val edited = manageLedger.updateTransaction(created.id, editDto(cardId, expenseId, 1_000, "Coffee"), user)

            assertEquals(setOf(cardId, expenseId), edited.splits.map { it.accountId }.toSet())
            assertEquals(mapOf(cashId to 0L, cardId to -1_000L, expenseId to 1_000L), projection(cashId, cardId, expenseId))
            assertEquals(2L, ledgerSplitRepository.count())
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("editing the currency converts split amounts into each account currency")
        fun editingCurrencyRecomputesConvertedAmounts() {
            val user = persistUser("integration-edit-currency")
            val usdCashId = requireNotNull(persistAccount(user, "Edit USD Cash", AccountType.ASSET, usd).id)
            val eurExpenseId = requireNotNull(persistAccount(user, "Edit EUR Expense", AccountType.EXPENSE, eur).id)
            persistRate("EUR", "USD", "1.20")
            val created = manageLedger.createTransaction(editDto(usdCashId, eurExpenseId, 1_000, "Trip", "EUR"), user)
            assertEquals(mapOf(usdCashId to -1_200L, eurExpenseId to 1_000L), projection(usdCashId, eurExpenseId))

            val edited = manageLedger.updateTransaction(created.id, editDto(usdCashId, eurExpenseId, 2_000, "Trip", "EUR"), user)

            val amountByAccount = edited.splits.associate { it.accountId to it.amountMinor }
            assertEquals(2_400L, amountByAccount.getValue(usdCashId))
            assertEquals(2_000L, amountByAccount.getValue(eurExpenseId))
            assertEquals(mapOf(usdCashId to -2_400L, eurExpenseId to 2_000L), projection(usdCashId, eurExpenseId))
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("failed edit validation leaves transaction, splits and projection unchanged")
        fun failedEditLeavesStateUnchanged() {
            val user = persistUser("integration-edit-rollback")
            val cashId = requireNotNull(persistAccount(user, "Rollback Cash", AccountType.ASSET).id)
            val expenseId = requireNotNull(persistAccount(user, "Rollback Expense", AccountType.EXPENSE).id)
            val archived = persistAccount(user, "Rollback Archived", AccountType.ASSET)
            val archivedId = requireNotNull(archived.id)
            val created = manageLedger.createTransaction(editDto(cashId, expenseId, 1_200, "Original"), user)
            archived.archive(Instant.parse("2026-07-12T10:00:00Z"))
            accountRepository.saveAndFlush(archived)

            val unbalanced =
                editDto(cashId, expenseId, 1_200, "Unbalanced").let { dto ->
                    dto.copy(splits = listOf(dto.splits[0], dto.splits[1].copy(amountMinor = 900)))
                }
            assertThrows(BadRequestException::class.java) {
                manageLedger.updateTransaction(created.id, unbalanced, user)
            }
            val archivedException =
                assertThrows(BadRequestException::class.java) {
                    manageLedger.updateTransaction(created.id, editDto(archivedId, expenseId, 1_200, "To archived"), user)
                }
            assertEquals("Archived accounts cannot be used in new transactions", archivedException.message)

            val persisted = ledgerTxnRepository.findById(created.id).orElseThrow()
            assertEquals("Original", persisted.description)
            assertEquals(1L, ledgerTxnRepository.count())
            assertEquals(2L, ledgerSplitRepository.count())
            assertEquals(mapOf(cashId to -1_200L, expenseId to 1_200L), projection(cashId, expenseId))
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("editing is rejected when the existing transaction touches an archived account")
        fun editingIsRejectedForArchivedOldAccount() {
            val user = persistUser("integration-edit-archived-old")
            val cash = persistAccount(user, "Old Archived Cash", AccountType.ASSET)
            val cashId = requireNotNull(cash.id)
            val expenseId = requireNotNull(persistAccount(user, "Old Expense", AccountType.EXPENSE).id)
            val otherCashId = requireNotNull(persistAccount(user, "Other Cash", AccountType.ASSET).id)
            val created = manageLedger.createTransaction(editDto(cashId, expenseId, 1_200, "History"), user)
            cash.archive(Instant.parse("2026-07-12T10:00:00Z"))
            accountRepository.saveAndFlush(cash)

            val exception =
                assertThrows(BadRequestException::class.java) {
                    manageLedger.updateTransaction(created.id, editDto(otherCashId, expenseId, 1_200, "Moved"), user)
                }

            assertEquals("Transactions involving archived accounts cannot be edited", exception.message)
            assertEquals(mapOf(cashId to -1_200L, expenseId to 1_200L), projection(cashId, expenseId))
            assertEquals(2L, ledgerSplitRepository.count())
        }

        @Test
        @DisplayName("editing another user's non-shared transaction is forbidden and changes nothing")
        fun editingForeignTransactionIsForbidden() {
            val owner = persistUser("integration-edit-owner")
            val intruder = persistUser("integration-edit-intruder")
            val cashId = requireNotNull(persistAccount(owner, "Foreign Cash", AccountType.ASSET).id)
            val expenseId = requireNotNull(persistAccount(owner, "Foreign Expense", AccountType.EXPENSE).id)
            val created = manageLedger.createTransaction(editDto(cashId, expenseId, 1_200, "Private"), owner)

            assertThrows(ForbiddenException::class.java) {
                manageLedger.updateTransaction(created.id, editDto(cashId, expenseId, 5_000, "Hijacked"), intruder)
            }

            assertEquals("Private", ledgerTxnRepository.findById(created.id).orElseThrow().description)
            assertEquals(mapOf(cashId to -1_200L, expenseId to 1_200L), projection(cashId, expenseId))
        }

        private fun editDto(
            cashId: UUID,
            expenseId: UUID,
            amountMinor: Long,
            description: String,
            currency: String = "EUR",
        ): CreateTransactionDto =
            CreateTransactionDto(
                txnDate = LocalDate.now(),
                currency = currency,
                description = description,
                splits =
                    listOf(
                        CreateSplitDto(accountId = cashId, side = "CREDIT", amountMinor = amountMinor),
                        CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = amountMinor),
                    ),
            )

        private fun projection(vararg accountIds: UUID): Map<UUID, Long> =
            accountCurrentBalanceRepository
                .findAllByAccountIdIn(accountIds.toList())
                .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }

        private fun persistUser(prefix: String): User =
            userRepository.save(
                User(
                    password = "encoded-password",
                    email = "$prefix-${UUID.randomUUID()}@test.com",
                ),
            )

        private fun persistAccount(
            user: User,
            name: String,
            type: AccountType,
            currency: Currency = eur,
        ): Account =
            accountRepository.save(
                Account(
                    owner = user,
                    name = "$name-${UUID.randomUUID()}",
                    type = type,
                    currency = currency,
                ),
            )

        private fun persistRate(
            baseCode: String,
            quoteCode: String,
            rate: String,
        ) {
            val base = requireNotNull(currencyRepository.findById(baseCode).orElseThrow())
            val quote = requireNotNull(currencyRepository.findById(quoteCode).orElseThrow())
            currencyExchangeRateRepository.save(
                CurrencyExchangeRate(
                    id = CurrencyExchangeRateId(baseCode, quoteCode),
                    base = base,
                    quote = quote,
                    rate = BigDecimal(rate),
                    providerDate = LocalDate.of(2026, 2, 20),
                    updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
                ),
            )
        }

        private fun postTxn(
            user: User,
            txnDate: LocalDate,
            description: String,
            vararg splits: Pair<UUID, String>,
        ) {
            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = txnDate,
                        currency = "EUR",
                        description = description,
                        splits =
                            splits.map { (accountId, side) ->
                                CreateSplitDto(accountId = accountId, side = side, amountMinor = 1_000)
                            },
                    ),
                creator = user,
            )
        }
    }
