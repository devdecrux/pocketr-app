package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.services.ledger.validations.DoubleEntryBalanceValidator
import com.decrux.pocketr.api.services.ledger.validations.MinimumSplitCountValidator
import com.decrux.pocketr.api.services.ledger.validations.PositiveSplitAmountValidator
import com.decrux.pocketr.api.services.ledger.validations.SplitSideValueValidator
import com.decrux.pocketr.api.services.ledger.validations.TransactionAccountCurrencyValidator
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("Ledger validation rules — Isolated split and currency validation")
class LedgerTransactionValidatorTest {
    private lateinit var minimumSplitCountValidator: MinimumSplitCountValidator
    private lateinit var positiveSplitAmountValidator: PositiveSplitAmountValidator
    private lateinit var splitSideValueValidator: SplitSideValueValidator
    private lateinit var doubleEntryBalanceValidator: DoubleEntryBalanceValidator
    private lateinit var transactionAccountCurrencyValidator: TransactionAccountCurrencyValidator

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")
    private val usd = Currency(code = "USD", minorUnit = 2, name = "US Dollar")
    private val owner = User(userId = 1L, password = "encoded", email = "alice@test.com")

    @BeforeEach
    fun setUp() {
        minimumSplitCountValidator = MinimumSplitCountValidator()
        positiveSplitAmountValidator = PositiveSplitAmountValidator()
        splitSideValueValidator = SplitSideValueValidator()
        doubleEntryBalanceValidator = DoubleEntryBalanceValidator()
        transactionAccountCurrencyValidator = TransactionAccountCurrencyValidator()
    }

    private fun validateSplits(splits: List<CreateSplitDto>) {
        minimumSplitCountValidator.validate(splits)
        positiveSplitAmountValidator.validate(splits)
        splitSideValueValidator.validate(splits)
        doubleEntryBalanceValidator.validate(splits)
    }

    @Nested
    @DisplayName("split validations")
    inner class ValidateSplits {
        @Test
        @DisplayName("should pass for valid balanced 2-split transaction")
        fun validBalancedSplits() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 5000),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 5000),
                )
            assertDoesNotThrow { validateSplits(splits) }
        }

        @Test
        @DisplayName("should pass for valid balanced 3-split transaction")
        fun validBalancedThreeSplits() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 10000),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 7000),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 3000),
                )
            assertDoesNotThrow { validateSplits(splits) }
        }

        @Test
        @DisplayName("should reject fewer than 2 splits")
        fun rejectFewerThan2() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 1000),
                )
            val ex =
                assertThrows(BadRequestException::class.java) {
                    validateSplits(splits)
                }
            assertTrue(ex.message!!.contains("at least 2 splits"))
        }

        @Test
        @DisplayName("should reject empty splits")
        fun rejectEmpty() {
            assertThrows(BadRequestException::class.java) {
                validateSplits(emptyList())
            }
        }

        @Test
        @DisplayName("should reject zero amount")
        fun rejectZeroAmount() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 0),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 0),
                )
            val ex =
                assertThrows(BadRequestException::class.java) {
                    validateSplits(splits)
                }
            assertTrue(ex.message!!.contains("greater than 0"))
        }

        @Test
        @DisplayName("should reject negative amount")
        fun rejectNegativeAmount() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = -500),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = -500),
                )
            val ex =
                assertThrows(BadRequestException::class.java) {
                    validateSplits(splits)
                }
            assertTrue(ex.message!!.contains("greater than 0"))
        }

        @Test
        @DisplayName("should reject invalid split side")
        fun rejectInvalidSide() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "INVALID", amountMinor = 1000),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 1000),
                )
            val ex =
                assertThrows(BadRequestException::class.java) {
                    validateSplits(splits)
                }
            assertTrue(ex.message!!.contains("Invalid split side"))
        }

        @Test
        @DisplayName("should reject unbalanced debits and credits")
        fun rejectUnbalanced() {
            val splits =
                listOf(
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 5000),
                    CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 4500),
                )
            val ex =
                assertThrows(BadRequestException::class.java) {
                    validateSplits(splits)
                }
            assertTrue(ex.message!!.contains("Double-entry violation"))
        }
    }

    @Nested
    @DisplayName("currency validation")
    inner class ValidateCurrencyConsistency {
        @Test
        @DisplayName("should pass when all accounts match transaction currency")
        fun allAccountsMatch() {
            val accounts =
                listOf(
                    Account(id = UUID.randomUUID(), owner = owner, name = "A1", type = AccountType.ASSET, currency = eur),
                    Account(id = UUID.randomUUID(), owner = owner, name = "A2", type = AccountType.EXPENSE, currency = eur),
                )
            assertDoesNotThrow { transactionAccountCurrencyValidator.validate(accounts, "EUR") }
        }

        @Test
        @DisplayName("should reject when an account has different currency")
        fun rejectMismatch() {
            val accounts =
                listOf(
                    Account(id = UUID.randomUUID(), owner = owner, name = "EUR Acct", type = AccountType.ASSET, currency = eur),
                    Account(id = UUID.randomUUID(), owner = owner, name = "USD Acct", type = AccountType.ASSET, currency = usd),
                )
            val ex =
                assertThrows(BadRequestException::class.java) {
                    transactionAccountCurrencyValidator.validate(accounts, "EUR")
                }
            assertTrue(ex.message!!.contains("currency"))
        }
    }
}
