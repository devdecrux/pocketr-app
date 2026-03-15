package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
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
import java.time.LocalDate
import java.util.*

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
    private val ledgerTxnRepository: LedgerTxnRepository,
    private val ledgerSplitRepository: LedgerSplitRepository,
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    private val currentAccountBalanceMonitor: CurrentAccountBalanceMonitor,
    ) {
    private lateinit var eur: Currency

    @BeforeEach
    fun cleanState() {
        accountCurrentBalanceRepository.deleteAll()
        ledgerTxnRepository.deleteAll()
        accountRepository.deleteAll()
        userRepository.deleteAll()

        eur =
            currencyRepository.findById("EUR").orElseGet {
                currencyRepository.save(
                    Currency(code = "EUR", minorUnit = 2, name = "Euro"),
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
        val expense = persistAccount(user, "Expense Gate", AccountType.EXPENSE)
        val cashId = requireNotNull(cash.id)
        val expenseId = requireNotNull(expense.id)
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
                            CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1_200),
                        ),
                ),
            creator = user,
        )

        accountCurrentBalanceRepository.addDelta(cashId, 5_000L)
        currentAccountBalanceMonitor.logIntegrityStatusOnStartup()

        // Tamper a healthy account after the integrity check to verify it still uses snapshot reads.
        accountCurrentBalanceRepository.addDelta(expenseId, 700L)

        val cashBalance = manageLedger.getAccountBalance(cashId, today, user, null)
        val expenseBalance = manageLedger.getAccountBalance(expenseId, today, user, null)
        assertEquals(-1_200L, cashBalance.balanceMinor)
        assertEquals(1_900L, expenseBalance.balanceMinor)
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
    ): Account =
        accountRepository.save(
            Account(
                owner = user,
                name = "$name-${UUID.randomUUID()}",
                type = type,
                currency = eur,
            ),
        )
}
