package com.decrux.pocketr.api.services.account

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr.api.entities.dtos.TransactionDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.repositories.LedgerSplitRepository
import com.decrux.pocketr.api.repositories.LedgerTxnRepository
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.services.ledger.CurrentAccountBalanceMonitor
import com.decrux.pocketr.api.services.ledger.ManageLedger
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@UsePostgresDb
@TestPropertySource(properties = ["ledger.accounts.snapshot.balance.enabled=true"])
@DisplayName("Account archival validation integration")
class AccountArchivalValidationIntegrationTest
    @Autowired
    constructor(
        private val manageAccount: ManageAccount,
        private val manageLedger: ManageLedger,
        private val userRepository: UserRepository,
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val ledgerTxnRepository: LedgerTxnRepository,
        private val ledgerSplitRepository: LedgerSplitRepository,
        private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
        private val currentAccountBalanceMonitor: CurrentAccountBalanceMonitor,
        transactionManager: PlatformTransactionManager,
    ) {
        private val transactionTemplate = TransactionTemplate(transactionManager)
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
                        Currency(code = "EUR", minorUnit = 2, name = "Euro", symbol = "€"),
                    )
                }

            currentAccountBalanceMonitor.logIntegrityStatusOnStartup()
        }

        @Test
        @DisplayName("non-zero ASSET account cannot be archived")
        fun rejectsNonZeroAssetAccount() {
            val user = persistUser("non-zero-asset")
            val asset = persistAccount(user, "Asset", AccountType.ASSET)
            val income = persistAccount(user, "Income", AccountType.INCOME)
            val assetId = requireNotNull(asset.id)

            postTransaction(
                user = user,
                description = "Fund asset",
                splits =
                    listOf(
                        CreateSplitDto(accountId = assetId, side = "DEBIT", amountMinor = 1_000),
                        CreateSplitDto(accountId = requireNotNull(income.id), side = "CREDIT", amountMinor = 1_000),
                    ),
            )

            val failure = runCatching { manageAccount.archiveAccount(assetId, user) }.exceptionOrNull()

            assertTrue(failure is BadRequestException, "Archiving a non-zero ASSET account must be rejected")
            assertEquals(AccountStatus.ACTIVE, findAccount(assetId).status)
            assertEquals(1_000L, authoritativeRawBalance(assetId))
        }

        @Test
        @DisplayName("non-zero LIABILITY account cannot be archived")
        fun rejectsNonZeroLiabilityAccount() {
            val user = persistUser("non-zero-liability")
            val asset = persistAccount(user, "Asset", AccountType.ASSET)
            val liability = persistAccount(user, "Liability", AccountType.LIABILITY)
            val liabilityId = requireNotNull(liability.id)

            postTransaction(
                user = user,
                description = "Borrow funds",
                splits =
                    listOf(
                        CreateSplitDto(accountId = requireNotNull(asset.id), side = "DEBIT", amountMinor = 1_000),
                        CreateSplitDto(accountId = liabilityId, side = "CREDIT", amountMinor = 1_000),
                    ),
            )

            val failure = runCatching { manageAccount.archiveAccount(liabilityId, user) }.exceptionOrNull()

            assertTrue(failure is BadRequestException, "Archiving a non-zero LIABILITY account must be rejected")
            assertEquals(AccountStatus.ACTIVE, findAccount(liabilityId).status)
            assertEquals(-1_000L, authoritativeRawBalance(liabilityId))
        }

        @Test
        @DisplayName("zero-balance ASSET and LIABILITY accounts can be archived")
        fun archivesZeroBalanceAssetAndLiabilityAccounts() {
            val user = persistUser("zero-balance")
            val accounts =
                listOf(
                    persistAccount(user, "Zero Asset", AccountType.ASSET),
                    persistAccount(user, "Zero Liability", AccountType.LIABILITY),
                )

            accounts.forEach { account ->
                val accountId = requireNotNull(account.id)
                manageAccount.archiveAccount(accountId, user)

                val archived = findAccount(accountId)
                assertEquals(AccountStatus.ARCHIVED, archived.status)
                assertTrue(archived.archivedAt != null)
                assertEquals(0L, authoritativeRawBalance(accountId))
            }
        }

        @Test
        @DisplayName("non-zero INCOME and EXPENSE accounts can be archived")
        fun archivesNonZeroIncomeAndExpenseAccounts() {
            val user = persistUser("non-zero-nominal")
            val asset = persistAccount(user, "Asset", AccountType.ASSET)
            val income = persistAccount(user, "Income", AccountType.INCOME)
            val expense = persistAccount(user, "Expense", AccountType.EXPENSE)
            val assetId = requireNotNull(asset.id)
            val incomeId = requireNotNull(income.id)
            val expenseId = requireNotNull(expense.id)

            postTransaction(
                user = user,
                description = "Earn income",
                splits =
                    listOf(
                        CreateSplitDto(accountId = assetId, side = "DEBIT", amountMinor = 1_000),
                        CreateSplitDto(accountId = incomeId, side = "CREDIT", amountMinor = 1_000),
                    ),
            )
            postTransaction(
                user = user,
                description = "Spend income",
                splits =
                    listOf(
                        CreateSplitDto(accountId = assetId, side = "CREDIT", amountMinor = 400),
                        CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 400),
                    ),
            )

            manageAccount.archiveAccount(incomeId, user)
            manageAccount.archiveAccount(expenseId, user)

            assertEquals(AccountStatus.ARCHIVED, findAccount(incomeId).status)
            assertEquals(AccountStatus.ARCHIVED, findAccount(expenseId).status)
            assertEquals(-1_000L, authoritativeRawBalance(incomeId))
            assertEquals(400L, authoritativeRawBalance(expenseId))
        }

        @Test
        @DisplayName("transactions using archived accounts cannot be deleted and remain in history")
        fun rejectsDeletionAndPreservesArchivedAccountHistoryAndBalance() {
            val user = persistUser("archived-delete")
            val asset = persistAccount(user, "Asset", AccountType.ASSET)
            val expense = persistAccount(user, "Expense", AccountType.EXPENSE)
            val expenseId = requireNotNull(expense.id)
            val transaction =
                postTransaction(
                    user = user,
                    description = "Preserve archived history",
                    splits =
                        listOf(
                            CreateSplitDto(accountId = requireNotNull(asset.id), side = "CREDIT", amountMinor = 700),
                            CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 700),
                        ),
                )
            manageAccount.archiveAccount(expenseId, user)

            val balanceBefore = manageLedger.getAccountBalance(expenseId, LocalDate.now(), user, null)
            val historyBefore = listTransactions(user)
            val transactionCountBefore = ledgerTxnRepository.count()
            val splitCountBefore = ledgerSplitRepository.count()

            val failure = runCatching { manageLedger.deleteTransaction(transaction.id, user) }.exceptionOrNull()

            val balanceAfter = manageLedger.getAccountBalance(expenseId, LocalDate.now(), user, null)
            val historyAfter = listTransactions(user)
            assertEquals(transactionCountBefore, ledgerTxnRepository.count())
            assertEquals(splitCountBefore, ledgerSplitRepository.count())
            assertEquals(historyBefore, historyAfter)
            assertEquals(balanceBefore, balanceAfter)
            assertTrue(failure is BadRequestException, "Deleting a transaction using an archived account must be rejected")
        }

        @Test
        @DisplayName("authoritative zero ledger balance permits archival when snapshot is unreliable")
        fun authoritativeZeroBalancePermitsArchiveWithUnreliableSnapshot() {
            val user = persistUser("unreliable-snapshot")
            val asset = persistAccount(user, "Asset", AccountType.ASSET)
            val assetId = requireNotNull(asset.id)

            transactionTemplate.executeWithoutResult {
                accountCurrentBalanceRepository.addDelta(assetId, 9_999L)
            }
            currentAccountBalanceMonitor.logIntegrityStatusOnStartup()

            assertEquals(9_999L, accountCurrentBalanceRepository.findById(assetId).orElseThrow().rawBalanceMinor)
            assertEquals(0L, authoritativeRawBalance(assetId))

            manageAccount.archiveAccount(assetId, user)

            assertEquals(AccountStatus.ARCHIVED, findAccount(assetId).status)
        }

        @Test
        @DisplayName("concurrent posting cannot leave an archived ASSET account non-zero")
        fun concurrentPostingAndAssetArchivePreserveInvariant() {
            assertPostingWinsRaceWithoutArchivedNonZero(AccountType.ASSET)
        }

        @Test
        @DisplayName("concurrent posting cannot leave an archived LIABILITY account non-zero")
        fun concurrentPostingAndLiabilityArchivePreserveInvariant() {
            assertPostingWinsRaceWithoutArchivedNonZero(AccountType.LIABILITY)
        }

        private fun assertPostingWinsRaceWithoutArchivedNonZero(targetType: AccountType) {
            val user = persistUser("archive-race-${targetType.name.lowercase()}")
            val target = persistAccount(user, "Race Target", targetType)
            val counterType = if (targetType == AccountType.ASSET) AccountType.INCOME else AccountType.ASSET
            val counter = persistAccount(user, "Race Counter", counterType)
            val targetId = requireNotNull(target.id)
            val targetSide = if (targetType == AccountType.ASSET) "DEBIT" else "CREDIT"
            val counterSide = if (targetType == AccountType.ASSET) "CREDIT" else "DEBIT"
            val postingLockHeld = CountDownLatch(1)
            val archiveAttemptStarted = CountDownLatch(1)
            val threadPool = Executors.newFixedThreadPool(2)

            try {
                val posting =
                    threadPool.submit {
                        transactionTemplate.executeWithoutResult {
                            accountRepository.findOneById(targetId).orElseThrow()
                            postingLockHeld.countDown()
                            check(archiveAttemptStarted.await(10, TimeUnit.SECONDS)) {
                                "Archival did not start while the posting lock was held"
                            }
                            postTransaction(
                                user = user,
                                description = "Concurrent posting",
                                splits =
                                    listOf(
                                        CreateSplitDto(accountId = targetId, side = targetSide, amountMinor = 500),
                                        CreateSplitDto(
                                            accountId = requireNotNull(counter.id),
                                            side = counterSide,
                                            amountMinor = 500,
                                        ),
                                    ),
                            )
                        }
                    }

                assertTrue(postingLockHeld.await(10, TimeUnit.SECONDS), "Posting did not acquire the account lock")
                val archival =
                    threadPool.submit<Throwable?> {
                        archiveAttemptStarted.countDown()
                        runCatching { manageAccount.archiveAccount(targetId, user) }.exceptionOrNull()
                    }

                posting.get(20, TimeUnit.SECONDS)
                val archivalFailure = archival.get(20, TimeUnit.SECONDS)
                val storedAccount = findAccount(targetId)
                val ledgerBalance = authoritativeRawBalance(targetId)

                assertTrue(
                    storedAccount.status != AccountStatus.ARCHIVED || ledgerBalance == 0L,
                    "Concurrent operations must never leave an archived $targetType account with a non-zero ledger balance",
                )
                assertEquals(AccountStatus.ACTIVE, storedAccount.status)
                assertTrue(archivalFailure is BadRequestException, "Archival must reject the committed non-zero balance")
                assertEquals(if (targetType == AccountType.ASSET) 500L else -500L, ledgerBalance)
            } finally {
                threadPool.shutdownNow()
                threadPool.awaitTermination(10, TimeUnit.SECONDS)
            }
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

        private fun postTransaction(
            user: User,
            description: String,
            splits: List<CreateSplitDto>,
        ): TransactionDto =
            manageLedger.createTransaction(
                dto =
                    CreateTransactionDto(
                        txnDate = LocalDate.now(),
                        currency = "EUR",
                        description = description,
                        splits = splits,
                    ),
                creator = user,
            )

        private fun authoritativeRawBalance(accountId: UUID): Long =
            ledgerSplitRepository.computeBalance(
                accountId,
                LocalDate.now(),
                SplitSide.DEBIT,
                SplitSide.CREDIT,
            )

        private fun findAccount(accountId: UUID): Account = accountRepository.findById(accountId).orElseThrow()

        private fun listTransactions(user: User): List<TransactionDto> =
            manageLedger
                .listTransactions(user, "INDIVIDUAL", null, null, null, null, null, false, 0, 20)
                .content
    }
