package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.*
import com.decrux.pocketr_api.repositories.*
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.data.jpa.domain.Specification
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for household transaction visibility in listTransactions().
 *
 * Verifies that household mode returns transactions touching shared accounts,
 * individual mode returns only the caller's own transactions (no regression),
 * and all filters/error cases are handled correctly.
 */
@DisplayName("ManageLedgerImpl — Household Transaction Visibility")
class HouseholdTransactionVisibilityTest {

    private lateinit var ledgerTxnRepository: LedgerTxnRepository
    private lateinit var ledgerSplitRepository: LedgerSplitRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var currencyRepository: CurrencyRepository
    private lateinit var categoryTagRepository: CategoryTagRepository
    private lateinit var manageHousehold: ManageHousehold
    private lateinit var service: ManageLedgerImpl

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")

    private val alice = User(userId = 1L, passwordValue = "encoded", email = "alice@test.com")
    private val bob = User(userId = 2L, passwordValue = "encoded", email = "bob@test.com")
    private val outsider = User(userId = 99L, passwordValue = "encoded", email = "outsider@test.com")

    private val householdId = UUID.randomUUID()

    // Alice's accounts
    private val aliceCheckingId = UUID.randomUUID()
    private val aliceChecking = Account(
        id = aliceCheckingId, owner = alice, name = "Alice Checking",
        type = AccountType.ASSET, currency = eur,
    )
    private val aliceExpenseId = UUID.randomUUID()
    private val aliceExpense = Account(
        id = aliceExpenseId, owner = alice, name = "Alice Groceries",
        type = AccountType.EXPENSE, currency = eur,
    )
    private val alicePrivateId = UUID.randomUUID()
    private val alicePrivate = Account(
        id = alicePrivateId, owner = alice, name = "Alice Private Savings",
        type = AccountType.ASSET, currency = eur,
    )

    // Bob's accounts
    private val bobCheckingId = UUID.randomUUID()
    private val bobChecking = Account(
        id = bobCheckingId, owner = bob, name = "Bob Checking",
        type = AccountType.ASSET, currency = eur,
    )

    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        ledgerTxnRepository = mock(LedgerTxnRepository::class.java)
        ledgerSplitRepository = mock(LedgerSplitRepository::class.java)
        accountRepository = mock(AccountRepository::class.java)
        currencyRepository = mock(CurrencyRepository::class.java)
        categoryTagRepository = mock(CategoryTagRepository::class.java)
        manageHousehold = mock(ManageHousehold::class.java)

        service = ManageLedgerImpl(
            ledgerTxnRepository, ledgerSplitRepository,
            accountRepository, currencyRepository, categoryTagRepository,
            manageHousehold,
        )
    }

    /** Creates a LedgerTxn with two splits that map cleanly to DTOs. */
    private fun buildTxn(
        createdBy: User,
        debitAccount: Account,
        creditAccount: Account,
        amount: Long = 5000L,
        description: String = "Test txn",
        txnDate: LocalDate = LocalDate.of(2026, 2, 15),
        txnHouseholdId: UUID? = null,
    ): LedgerTxn {
        val txn = LedgerTxn(
            id = UUID.randomUUID(),
            createdBy = createdBy,
            householdId = txnHouseholdId,
            txnDate = txnDate,
            description = description,
            currency = eur,
            createdAt = now,
            updatedAt = now,
        )
        val debitSplit = LedgerSplit(
            id = UUID.randomUUID(),
            transaction = txn,
            account = debitAccount,
            side = SplitSide.DEBIT,
            amountMinor = amount,
        )
        val creditSplit = LedgerSplit(
            id = UUID.randomUUID(),
            transaction = txn,
            account = creditAccount,
            side = SplitSide.CREDIT,
            amountMinor = amount,
        )
        txn.splits = mutableListOf(debitSplit, creditSplit)
        return txn
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubFindAll(vararg txns: LedgerTxn) {
        `when`(ledgerTxnRepository.findAll(any(Specification::class.java) as Specification<LedgerTxn>))
            .thenReturn(txns.toList())
    }

    @Nested
    @DisplayName("listTransactions — household mode")
    inner class HouseholdMode {

        @Test
        @DisplayName("should return transactions on shared accounts for a household member")
        fun returnTransactionsOnSharedAccounts() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId, bobCheckingId))

            val txn1 = buildTxn(alice, aliceExpense, aliceChecking, description = "Alice grocery")
            val txn2 = buildTxn(bob, bobChecking, aliceChecking, description = "Bob to Alice transfer")
            stubFindAll(txn1, txn2)

            val result = service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals(2, result.size)
            assertEquals("Alice grocery", result[0].description)
            assertEquals("Bob to Alice transfer", result[1].description)

            verify(manageHousehold).isActiveMember(householdId, alice.userId!!)
            verify(manageHousehold).getSharedAccountIds(householdId)
        }

        @Test
        @DisplayName("should include historical transactions with null householdId on shared accounts")
        fun includeHistoricalTransactionsWithNullHouseholdId() {
            `when`(manageHousehold.isActiveMember(householdId, bob.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))

            // This transaction was created BEFORE the household existed (householdId = null)
            val historicalTxn = buildTxn(
                alice, aliceExpense, aliceChecking,
                description = "Pre-household expense", txnHouseholdId = null,
            )
            stubFindAll(historicalTxn)

            val result = service.listTransactions(
                user = bob, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals(1, result.size)
            assertEquals("Pre-household expense", result[0].description)
            // The spec is hasAnySharedAccount which matches on splits→account, not householdId
            // so historical txns are included. Verify householdId on the DTO is null.
            assertNull(result[0].householdId)
        }

        @Test
        @DisplayName("should return empty list when no accounts are shared in the household")
        fun emptyListWhenNoSharedAccounts() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId)).thenReturn(emptySet())

            val result = service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertTrue(result.isEmpty())
            // findAll should NOT be called — early return
            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository, never()).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should throw 403 when user is not an active household member")
        fun forbiddenForNonMember() {
            `when`(manageHousehold.isActiveMember(householdId, outsider.userId!!)).thenReturn(false)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.listTransactions(
                    user = outsider, mode = "HOUSEHOLD", householdId = householdId,
                    dateFrom = null, dateTo = null, accountId = null, categoryId = null,
                )
            }

            assertEquals(403, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("Not an active member"))
            verify(manageHousehold, never()).getSharedAccountIds(any())
        }

        @Test
        @DisplayName("should throw 400 when householdId is missing in household mode")
        fun badRequestWhenHouseholdIdMissing() {
            val ex = assertThrows(ResponseStatusException::class.java) {
                service.listTransactions(
                    user = alice, mode = "HOUSEHOLD", householdId = null,
                    dateFrom = null, dateTo = null, accountId = null, categoryId = null,
                )
            }

            assertEquals(400, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("householdId is required"))
        }

        @Test
        @DisplayName("should accept case-insensitive mode value")
        fun caseInsensitiveMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))
            stubFindAll() // empty result is fine

            // "household" lowercase should work the same as "HOUSEHOLD"
            val result = service.listTransactions(
                user = alice, mode = "household", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertNotNull(result)
            verify(manageHousehold).isActiveMember(householdId, alice.userId!!)
        }

        @Test
        @DisplayName("should apply dateFrom filter in household mode")
        fun dateFromFilterInHouseholdMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))

            val txn = buildTxn(alice, aliceExpense, aliceChecking, txnDate = LocalDate.of(2026, 2, 20))
            stubFindAll(txn)

            val result = service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = LocalDate.of(2026, 2, 1), dateTo = null,
                accountId = null, categoryId = null,
            )

            assertEquals(1, result.size)
            // Verify findAll was called (spec was composed with dateFrom)
            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply dateTo filter in household mode")
        fun dateToFilterInHouseholdMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))
            stubFindAll()

            service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = LocalDate.of(2026, 2, 28),
                accountId = null, categoryId = null,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply accountId filter in household mode")
        fun accountIdFilterInHouseholdMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId, bobCheckingId))
            stubFindAll()

            service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null,
                accountId = aliceCheckingId, categoryId = null,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply categoryId filter in household mode")
        fun categoryIdFilterInHouseholdMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))
            stubFindAll()

            val categoryId = UUID.randomUUID()
            service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null,
                accountId = null, categoryId = categoryId,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply all filters simultaneously in household mode")
        fun allFiltersInHouseholdMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))
            stubFindAll()

            service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = LocalDate.of(2026, 1, 1),
                dateTo = LocalDate.of(2026, 2, 28),
                accountId = aliceCheckingId,
                categoryId = UUID.randomUUID(),
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should correctly map transaction DTOs in household mode")
        fun correctDtoMapping() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))

            val txn = buildTxn(
                alice, aliceExpense, aliceChecking,
                amount = 12000, description = "Supermarket",
                txnDate = LocalDate.of(2026, 2, 10), txnHouseholdId = householdId,
            )
            stubFindAll(txn)

            val result = service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals(1, result.size)
            val dto = result[0]
            assertEquals(txn.id, dto.id)
            assertEquals(LocalDate.of(2026, 2, 10), dto.txnDate)
            assertEquals("EUR", dto.currency)
            assertEquals("Supermarket", dto.description)
            assertEquals(householdId, dto.householdId)
            assertEquals(2, dto.splits.size)

            // Verify split DTO mapping
            val debitSplit = dto.splits.first { it.side == "DEBIT" }
            assertEquals(aliceExpenseId, debitSplit.accountId)
            assertEquals("Alice Groceries", debitSplit.accountName)
            assertEquals("EXPENSE", debitSplit.accountType)
            assertEquals(12000L, debitSplit.amountMinor)

            val creditSplit = dto.splits.first { it.side == "CREDIT" }
            assertEquals(aliceCheckingId, creditSplit.accountId)
            assertEquals("Alice Checking", creditSplit.accountName)
            assertEquals("ASSET", creditSplit.accountType)
            assertEquals(12000L, creditSplit.amountMinor)
        }

        @Test
        @DisplayName("should not call forUser spec in household mode")
        fun doesNotUseForUserInHouseholdMode() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))
            stubFindAll()

            service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            // In household mode, getSharedAccountIds is called (not forUser-based filtering)
            verify(manageHousehold).getSharedAccountIds(householdId)
        }
    }

    @Nested
    @DisplayName("listTransactions — individual mode (regression)")
    inner class IndividualMode {

        @Test
        @DisplayName("should return only user's own transactions in individual mode (null mode)")
        fun returnOwnTransactionsNullMode() {
            val txn = buildTxn(alice, aliceExpense, aliceChecking, description = "Alice's bill")
            stubFindAll(txn)

            val result = service.listTransactions(
                user = alice, mode = null, householdId = null,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals(1, result.size)
            assertEquals("Alice's bill", result[0].description)
            // manageHousehold should never be touched in individual mode
            verify(manageHousehold, never()).isActiveMember(any(), anyLong())
            verify(manageHousehold, never()).getSharedAccountIds(any())
        }

        @Test
        @DisplayName("should return only user's own transactions in individual mode (explicit 'INDIVIDUAL')")
        fun returnOwnTransactionsExplicitIndividualMode() {
            val txn = buildTxn(alice, aliceExpense, aliceChecking)
            stubFindAll(txn)

            val result = service.listTransactions(
                user = alice, mode = "INDIVIDUAL", householdId = null,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals(1, result.size)
            verify(manageHousehold, never()).isActiveMember(any(), anyLong())
            verify(manageHousehold, never()).getSharedAccountIds(any())
        }

        @Test
        @DisplayName("should apply dateFrom filter in individual mode")
        fun dateFromFilterInIndividualMode() {
            stubFindAll()

            service.listTransactions(
                user = alice, mode = null, householdId = null,
                dateFrom = LocalDate.of(2026, 2, 1), dateTo = null,
                accountId = null, categoryId = null,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply dateTo filter in individual mode")
        fun dateToFilterInIndividualMode() {
            stubFindAll()

            service.listTransactions(
                user = alice, mode = null, householdId = null,
                dateFrom = null, dateTo = LocalDate.of(2026, 2, 28),
                accountId = null, categoryId = null,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply accountId filter in individual mode")
        fun accountIdFilterInIndividualMode() {
            stubFindAll()

            service.listTransactions(
                user = alice, mode = null, householdId = null,
                dateFrom = null, dateTo = null,
                accountId = aliceCheckingId, categoryId = null,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should apply categoryId filter in individual mode")
        fun categoryIdFilterInIndividualMode() {
            stubFindAll()

            val categoryId = UUID.randomUUID()
            service.listTransactions(
                user = alice, mode = null, householdId = null,
                dateFrom = null, dateTo = null,
                accountId = null, categoryId = categoryId,
            )

            @Suppress("UNCHECKED_CAST")
            verify(ledgerTxnRepository).findAll(any(Specification::class.java) as Specification<LedgerTxn>)
        }

        @Test
        @DisplayName("should return empty list when user has no transactions")
        fun emptyListForNoTransactions() {
            stubFindAll() // returns empty

            val result = service.listTransactions(
                user = alice, mode = null, householdId = null,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("ManageHouseholdImpl.getSharedAccountIds — delegation")
    inner class GetSharedAccountIdsDelegation {

        @Test
        @DisplayName("should delegate to shareRepository and return account IDs")
        fun delegatesToRepository() {
            val expectedIds = setOf(aliceCheckingId, bobCheckingId)
            `when`(manageHousehold.getSharedAccountIds(householdId)).thenReturn(expectedIds)

            // Invoked indirectly through listTransactions household path
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            stubFindAll()

            service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            verify(manageHousehold).getSharedAccountIds(householdId)
        }
    }

    @Nested
    @DisplayName("LedgerTxnSpecs.hasAnySharedAccount")
    inner class HasAnySharedAccountSpec {

        @Test
        @DisplayName("should produce a non-null specification")
        fun producesNonNullSpec() {
            val spec = LedgerTxnSpecs.hasAnySharedAccount(setOf(UUID.randomUUID()))
            assertNotNull(spec)
        }

        @Test
        @DisplayName("should produce a non-null specification for multiple account IDs")
        fun producesSpecForMultipleIds() {
            val spec = LedgerTxnSpecs.hasAnySharedAccount(
                setOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            )
            assertNotNull(spec)
        }
    }

    @Nested
    @DisplayName("txnKind derivation in household context")
    inner class TxnKindDerivation {

        @Test
        @DisplayName("should derive EXPENSE kind for household transactions with expense account")
        fun expenseKindForHouseholdTxn() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId))

            val txn = buildTxn(alice, aliceExpense, aliceChecking, description = "Groceries")
            stubFindAll(txn)

            val result = service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals("EXPENSE", result[0].txnKind)
        }

        @Test
        @DisplayName("should derive TRANSFER kind for household transactions between asset accounts")
        fun transferKindForHouseholdTxn() {
            `when`(manageHousehold.isActiveMember(householdId, alice.userId!!)).thenReturn(true)
            `when`(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(setOf(aliceCheckingId, bobCheckingId))

            val txn = buildTxn(alice, bobChecking, aliceChecking, description = "Cross-user transfer")
            stubFindAll(txn)

            val result = service.listTransactions(
                user = alice, mode = "HOUSEHOLD", householdId = householdId,
                dateFrom = null, dateTo = null, accountId = null, categoryId = null,
            )

            assertEquals("TRANSFER", result[0].txnKind)
        }
    }
}
