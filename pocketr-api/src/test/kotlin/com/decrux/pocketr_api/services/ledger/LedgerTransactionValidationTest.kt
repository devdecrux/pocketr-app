package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.*
import com.decrux.pocketr_api.entities.dtos.CreateSplitDto
import com.decrux.pocketr_api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr_api.exceptions.DomainHttpException
import com.decrux.pocketr_api.repositories.*
import com.decrux.pocketr_api.services.household.ManageHousehold
import com.decrux.pocketr_api.services.user_avatar.UserAvatarService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for ledger transaction validation (Section 12.1).
 *
 * Tests the ManageLedgerImpl service with mocked repositories to verify
 * all double-entry invariants, currency rules, ownership permissions,
 * and category tag ownership validation.
 *
 * User entity uses Long IDs (not UUID).
 */
@DisplayName("ManageLedgerImpl — Transaction Validation")
class LedgerTransactionValidationTest {

    private lateinit var ledgerTxnRepository: LedgerTxnRepository
    private lateinit var ledgerSplitRepository: LedgerSplitRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var currencyRepository: CurrencyRepository
    private lateinit var categoryTagRepository: CategoryTagRepository
    private lateinit var manageHousehold: ManageHousehold
    private lateinit var userAvatarService: UserAvatarService
    private lateinit var transactionValidator: LedgerTransactionValidator
    private lateinit var transactionPolicy: LedgerTransactionPolicy
    private lateinit var service: ManageLedgerImpl

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")
    private val usd = Currency(code = "USD", minorUnit = 2, name = "US Dollar")

    private val userA = User(userId = 1L, passwordValue = "encoded", email = "alice@test.com")
    private val userB = User(userId = 2L, passwordValue = "encoded", email = "bob@test.com")

    private val checkingId = UUID.randomUUID()
    private val savingsId = UUID.randomUUID()
    private val expenseId = UUID.randomUUID()
    private val incomeId = UUID.randomUUID()
    private val liabilityId = UUID.randomUUID()
    private val equityId = UUID.randomUUID()

    private val checking = Account(id = checkingId, owner = userA, name = "Checking", type = AccountType.ASSET, currency = eur)
    private val savings = Account(id = savingsId, owner = userA, name = "Savings", type = AccountType.ASSET, currency = eur)
    private val expenseAcct = Account(id = expenseId, owner = userA, name = "Bills", type = AccountType.EXPENSE, currency = eur)
    private val incomeAcct = Account(id = incomeId, owner = userA, name = "Salary", type = AccountType.INCOME, currency = eur)
    private val liabilityAcct = Account(id = liabilityId, owner = userA, name = "Mortgage", type = AccountType.LIABILITY, currency = eur)
    private val equityAcct = Account(id = equityId, owner = userA, name = "Opening Equity", type = AccountType.EQUITY, currency = eur)

    private val userBSavingsId = UUID.randomUUID()
    private val userBSavings = Account(id = userBSavingsId, owner = userB, name = "Bob Savings", type = AccountType.ASSET, currency = eur)
    private val userBExpenseId = UUID.randomUUID()
    private val userBExpense = Account(id = userBExpenseId, owner = userB, name = "Bob Groceries", type = AccountType.EXPENSE, currency = eur)

    private val usdAccountId = UUID.randomUUID()
    private val usdAccount = Account(id = usdAccountId, owner = userA, name = "USD Checking", type = AccountType.ASSET, currency = usd)

    @BeforeEach
    fun setUp() {
        ledgerTxnRepository = mock(LedgerTxnRepository::class.java)
        ledgerSplitRepository = mock(LedgerSplitRepository::class.java)
        accountRepository = mock(AccountRepository::class.java)
        currencyRepository = mock(CurrencyRepository::class.java)
        categoryTagRepository = mock(CategoryTagRepository::class.java)
        manageHousehold = mock(ManageHousehold::class.java)
        userAvatarService = mock(UserAvatarService::class.java)
        transactionValidator = LedgerTransactionValidator()
        transactionPolicy = LedgerTransactionPolicy(manageHousehold)

        service = ManageLedgerImpl(
            ledgerTxnRepository, ledgerSplitRepository,
            accountRepository, currencyRepository, categoryTagRepository,
            manageHousehold, userAvatarService,
            transactionValidator, transactionPolicy,
        )

        `when`(currencyRepository.findById("EUR")).thenReturn(Optional.of(eur))
        `when`(currencyRepository.findById("USD")).thenReturn(Optional.of(usd))

        // Default: save returns input with ID set
        `when`(ledgerTxnRepository.save(any(LedgerTxn::class.java))).thenAnswer { invocation ->
            val txn = invocation.getArgument<LedgerTxn>(0)
            txn.id = UUID.randomUUID()
            txn.splits.forEach { it.id = UUID.randomUUID() }
            txn
        }
    }

    private fun stubAccounts(vararg accounts: Account) {
        val ids = accounts.map { requireNotNull(it.id) }
        `when`(accountRepository.findAllById(ids)).thenReturn(accounts.toList())
    }

    private fun validExpenseDto() = CreateTransactionDto(
        txnDate = LocalDate.of(2026, 2, 15),
        currency = "EUR",
        description = "Electricity bill",
        splits = listOf(
            CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 4500),
            CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 4500),
        ),
    )

    @Nested
    @DisplayName("Double-entry invariants")
    inner class DoubleEntryInvariants {

        @Test
        @DisplayName("should reject transaction with fewer than 2 splits")
        fun rejectFewerThan2Splits() {
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Test",
                splits = listOf(CreateSplitDto(accountId = checkingId, side = "DEBIT", amountMinor = 1000)),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("at least 2 splits"))
        }

        @Test
        @DisplayName("should reject transaction with 0 splits")
        fun rejectZeroSplits() {
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Test",
                splits = emptyList(),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
        }

        @Test
        @DisplayName("should reject transaction where total debits != total credits")
        fun rejectDebitsNotEqualCredits() {
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Unbalanced",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "DEBIT", amountMinor = 5000),
                    CreateSplitDto(accountId = expenseId, side = "CREDIT", amountMinor = 4500),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Double-entry violation"))
        }

        @Test
        @DisplayName("should reject transaction where any split amount is zero")
        fun rejectZeroAmount() {
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Zero",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 0),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 0),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("greater than 0"))
        }

        @Test
        @DisplayName("should reject transaction where any split amount is negative")
        fun rejectNegativeAmount() {
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Negative",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = -1000),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = -1000),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("greater than 0"))
        }

        @Test
        @DisplayName("should reject transaction with invalid split side")
        fun rejectInvalidSplitSide() {
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Bad side",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "INVALID", amountMinor = 1000),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1000),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Invalid split side"))
        }
    }

    @Nested
    @DisplayName("Currency consistency")
    inner class CurrencyConsistency {

        @Test
        @DisplayName("should reject transaction where account currency != transaction currency")
        fun rejectCurrencyMismatch() {
            stubAccounts(usdAccount, expenseAcct)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Mismatch",
                splits = listOf(
                    CreateSplitDto(accountId = usdAccountId, side = "CREDIT", amountMinor = 4500),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 4500),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("currency"))
        }

        @Test
        @DisplayName("should reject transaction with unknown currency code")
        fun rejectUnknownCurrency() {
            `when`(currencyRepository.findById("XYZ")).thenReturn(Optional.empty())
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "XYZ", description = "Unknown currency",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 1000),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1000),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Invalid currency"))
        }
    }

    @Nested
    @DisplayName("Ownership permissions (individual mode)")
    inner class OwnershipPermissions {

        @Test
        @DisplayName("should reject posting to non-owned account in individual mode")
        fun rejectPostingToNonOwnedAccount() {
            stubAccounts(checking, userBSavings)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Not my account",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 5000),
                    CreateSplitDto(accountId = userBSavingsId, side = "DEBIT", amountMinor = 5000),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(403, ex.status.value())
            assertTrue(ex.message!!.contains("not owned by current user"))
        }

        @Test
        @DisplayName("should allow posting to own accounts in individual mode")
        fun allowPostingToOwnAccounts() {
            stubAccounts(checking, expenseAcct)
            val dto = validExpenseDto()

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
            assertEquals(2, result.splits.size)
            verify(ledgerTxnRepository).save(any(LedgerTxn::class.java))
        }

        @Test
        @DisplayName("should reject transaction with category tag owned by another user")
        fun rejectCategoryTagOwnedByAnotherUser() {
            stubAccounts(checking, expenseAcct)
            val bobTagId = UUID.randomUUID()
            val bobTag = CategoryTag(id = bobTagId, owner = userB, name = "Bob's Food")
            `when`(categoryTagRepository.findAllById(listOf(bobTagId))).thenReturn(listOf(bobTag))

            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "With someone else's tag",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 5000),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 5000, categoryTagId = bobTagId),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(403, ex.status.value())
            assertTrue(ex.message!!.contains("not owned by current user"))
        }

        @Test
        @DisplayName("should reject transaction with non-existent category tag")
        fun rejectNonExistentCategoryTag() {
            stubAccounts(checking, expenseAcct)
            val missingTagId = UUID.randomUUID()
            `when`(categoryTagRepository.findAllById(listOf(missingTagId))).thenReturn(emptyList())

            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Missing tag",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 5000),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 5000, categoryTagId = missingTagId),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Category tags not found"))
        }

        @Test
        @DisplayName("should reject transaction with non-existent account")
        fun rejectNonExistentAccount() {
            val missingId = UUID.randomUUID()
            `when`(accountRepository.findAllById(listOf(checkingId, missingId))).thenReturn(listOf(checking))

            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Missing account",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 5000),
                    CreateSplitDto(accountId = missingId, side = "DEBIT", amountMinor = 5000),
                ),
            )

            val ex = assertThrows(DomainHttpException::class.java) {
                service.createTransaction(dto, userA)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Accounts not found"))
        }
    }

    @Nested
    @DisplayName("Valid transaction scenarios")
    inner class ValidTransactionScenarios {

        @Test
        @DisplayName("should succeed for expense transaction (ASSET credit, EXPENSE debit)")
        fun validExpenseTransaction() {
            stubAccounts(checking, expenseAcct)
            val tagId = UUID.randomUUID()
            val tag = CategoryTag(id = tagId, owner = userA, name = "Electricity")
            `when`(categoryTagRepository.findAllById(listOf(tagId))).thenReturn(listOf(tag))

            val dto = CreateTransactionDto(
                txnDate = LocalDate.of(2026, 2, 15), currency = "EUR", description = "Electricity bill",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 4500),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 4500, categoryTagId = tagId),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
            assertEquals("EUR", result.currency)
            assertEquals("Electricity bill", result.description)
            assertEquals(2, result.splits.size)
        }

        @Test
        @DisplayName("should succeed for income transaction (INCOME credit, ASSET debit)")
        fun validIncomeTransaction() {
            stubAccounts(checking, incomeAcct)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.of(2026, 1, 31), currency = "EUR", description = "January salary",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "DEBIT", amountMinor = 200000),
                    CreateSplitDto(accountId = incomeId, side = "CREDIT", amountMinor = 200000),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
            assertEquals(2, result.splits.size)
        }

        @Test
        @DisplayName("should succeed for transfer between own ASSET accounts")
        fun validTransferTransaction() {
            stubAccounts(checking, savings)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Move to savings",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 30000),
                    CreateSplitDto(accountId = savingsId, side = "DEBIT", amountMinor = 30000),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
        }

        @Test
        @DisplayName("should succeed for liability payment (ASSET credit, LIABILITY debit)")
        fun validLiabilityPayment() {
            stubAccounts(checking, liabilityAcct)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Mortgage payment",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 50000),
                    CreateSplitDto(accountId = liabilityId, side = "DEBIT", amountMinor = 50000),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
        }

        @Test
        @DisplayName("should succeed for opening balance (EQUITY credit, ASSET debit)")
        fun validOpeningBalance() {
            stubAccounts(checking, equityAcct)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Opening balance",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "DEBIT", amountMinor = 100000),
                    CreateSplitDto(accountId = equityId, side = "CREDIT", amountMinor = 100000),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
        }

        @Test
        @DisplayName("should succeed for multi-split expense transaction")
        fun validMultiSplitExpense() {
            val foodId = UUID.randomUUID()
            val householdExpId = UUID.randomUUID()
            val food = Account(id = foodId, owner = userA, name = "Food", type = AccountType.EXPENSE, currency = eur)
            val householdExp = Account(id = householdExpId, owner = userA, name = "Household", type = AccountType.EXPENSE, currency = eur)
            stubAccounts(checking, food, householdExp)

            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Grocery trip",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 10000),
                    CreateSplitDto(accountId = foodId, side = "DEBIT", amountMinor = 7000),
                    CreateSplitDto(accountId = householdExpId, side = "DEBIT", amountMinor = 3000),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
            assertEquals(3, result.splits.size)
        }

        @Test
        @DisplayName("should trim description whitespace")
        fun trimDescription() {
            stubAccounts(checking, expenseAcct)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "  Trimmed  ",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 1000),
                    CreateSplitDto(accountId = expenseId, side = "DEBIT", amountMinor = 1000),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertEquals("Trimmed", result.description)
        }

        @Test
        @DisplayName("should allow transaction with null category tags")
        fun allowNullCategoryTags() {
            stubAccounts(checking, savings)
            val dto = CreateTransactionDto(
                txnDate = LocalDate.now(), currency = "EUR", description = "Transfer",
                splits = listOf(
                    CreateSplitDto(accountId = checkingId, side = "CREDIT", amountMinor = 5000, categoryTagId = null),
                    CreateSplitDto(accountId = savingsId, side = "DEBIT", amountMinor = 5000, categoryTagId = null),
                ),
            )

            val result = service.createTransaction(dto, userA)
            assertNotNull(result.id)
            // No categoryTagRepository interactions when no tags
            verify(categoryTagRepository, never()).findAllById(any())
        }
    }

    @Nested
    @DisplayName("Balance computation")
    inner class BalanceComputation {

        @Test
        @DisplayName("should compute debit-normal balance for ASSET account")
        fun debitNormalBalanceForAsset() {
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking))
            `when`(ledgerSplitRepository.computeBalance(checkingId, LocalDate.now(), SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(190000L)

            val result = service.getAccountBalance(checkingId, LocalDate.now(), userA, null)
            assertEquals(190000L, result.balanceMinor)
            assertEquals("ASSET", result.accountType)
            assertEquals("Checking", result.accountName)
            assertEquals("EUR", result.currency)
        }

        @Test
        @DisplayName("should compute debit-normal balance for EXPENSE account")
        fun debitNormalBalanceForExpense() {
            `when`(accountRepository.findById(expenseId)).thenReturn(Optional.of(expenseAcct))
            `when`(ledgerSplitRepository.computeBalance(expenseId, LocalDate.now(), SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(8500L)

            val result = service.getAccountBalance(expenseId, LocalDate.now(), userA, null)
            assertEquals(8500L, result.balanceMinor)
            assertEquals("EXPENSE", result.accountType)
        }

        @Test
        @DisplayName("should compute credit-normal balance for LIABILITY account")
        fun creditNormalBalanceForLiability() {
            `when`(accountRepository.findById(liabilityId)).thenReturn(Optional.of(liabilityAcct))
            `when`(ledgerSplitRepository.computeBalance(liabilityId, LocalDate.now(), SplitSide.CREDIT, SplitSide.DEBIT))
                .thenReturn(49950000L)

            val result = service.getAccountBalance(liabilityId, LocalDate.now(), userA, null)
            assertEquals(49950000L, result.balanceMinor)
            assertEquals("LIABILITY", result.accountType)
        }

        @Test
        @DisplayName("should compute credit-normal balance for INCOME account")
        fun creditNormalBalanceForIncome() {
            `when`(accountRepository.findById(incomeId)).thenReturn(Optional.of(incomeAcct))
            `when`(ledgerSplitRepository.computeBalance(incomeId, LocalDate.now(), SplitSide.CREDIT, SplitSide.DEBIT))
                .thenReturn(400000L)

            val result = service.getAccountBalance(incomeId, LocalDate.now(), userA, null)
            assertEquals(400000L, result.balanceMinor)
            assertEquals("INCOME", result.accountType)
        }

        @Test
        @DisplayName("should compute credit-normal balance for EQUITY account")
        fun creditNormalBalanceForEquity() {
            `when`(accountRepository.findById(equityId)).thenReturn(Optional.of(equityAcct))
            `when`(ledgerSplitRepository.computeBalance(equityId, LocalDate.now(), SplitSide.CREDIT, SplitSide.DEBIT))
                .thenReturn(150000L)

            val result = service.getAccountBalance(equityId, LocalDate.now(), userA, null)
            assertEquals(150000L, result.balanceMinor)
            assertEquals("EQUITY", result.accountType)
        }

        @Test
        @DisplayName("should use provided as-of date for balance query")
        fun usesAsOfDate() {
            val asOf = LocalDate.of(2026, 1, 31)
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking))
            `when`(ledgerSplitRepository.computeBalance(checkingId, asOf, SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(100000L)

            val result = service.getAccountBalance(checkingId, asOf, userA, null)
            assertEquals(100000L, result.balanceMinor)
            assertEquals(asOf, result.asOf)
        }

        @Test
        @DisplayName("should reject balance query for non-owned account")
        fun rejectBalanceForNonOwnedAccount() {
            `when`(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking))

            val ex = assertThrows(DomainHttpException::class.java) {
                service.getAccountBalance(checkingId, LocalDate.now(), userB, null)
            }
            assertEquals(403, ex.status.value())
        }

        @Test
        @DisplayName("should return 404 for non-existent account balance query")
        fun notFoundForMissingAccount() {
            val missingId = UUID.randomUUID()
            `when`(accountRepository.findById(missingId)).thenReturn(Optional.empty())

            val ex = assertThrows(DomainHttpException::class.java) {
                service.getAccountBalance(missingId, LocalDate.now(), userA, null)
            }
            assertEquals(404, ex.status.value())
        }
    }
}
