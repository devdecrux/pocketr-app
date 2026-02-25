package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.dtos.CreateSplitDto
import com.decrux.pocketr_api.exceptions.DomainHttpException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("LedgerTransactionValidator — Isolated split and currency validation")
class LedgerTransactionValidatorTest {

    private lateinit var validator: LedgerTransactionValidator

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")
    private val usd = Currency(code = "USD", minorUnit = 2, name = "US Dollar")
    private val owner = User(userId = 1L, passwordValue = "encoded", email = "alice@test.com")

    @BeforeEach
    fun setUp() {
        validator = LedgerTransactionValidator()
    }

    @Nested
    @DisplayName("validateSplits")
    inner class ValidateSplits {

        @Test
        @DisplayName("should pass for valid balanced 2-split transaction")
        fun validBalancedSplits() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 5000),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 5000),
            )
            assertDoesNotThrow { validator.validateSplits(splits) }
        }

        @Test
        @DisplayName("should pass for valid balanced 3-split transaction")
        fun validBalancedThreeSplits() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 10000),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 7000),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 3000),
            )
            assertDoesNotThrow { validator.validateSplits(splits) }
        }

        @Test
        @DisplayName("should reject fewer than 2 splits")
        fun rejectFewerThan2() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 1000),
            )
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateSplits(splits)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("at least 2 splits"))
        }

        @Test
        @DisplayName("should reject empty splits")
        fun rejectEmpty() {
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateSplits(emptyList())
            }
            assertEquals(400, ex.status.value())
        }

        @Test
        @DisplayName("should reject zero amount")
        fun rejectZeroAmount() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 0),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 0),
            )
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateSplits(splits)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("greater than 0"))
        }

        @Test
        @DisplayName("should reject negative amount")
        fun rejectNegativeAmount() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = -500),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = -500),
            )
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateSplits(splits)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("greater than 0"))
        }

        @Test
        @DisplayName("should reject invalid split side")
        fun rejectInvalidSide() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "INVALID", amountMinor = 1000),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 1000),
            )
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateSplits(splits)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Invalid split side"))
        }

        @Test
        @DisplayName("should reject unbalanced debits and credits")
        fun rejectUnbalanced() {
            val splits = listOf(
                CreateSplitDto(accountId = UUID.randomUUID(), side = "DEBIT", amountMinor = 5000),
                CreateSplitDto(accountId = UUID.randomUUID(), side = "CREDIT", amountMinor = 4500),
            )
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateSplits(splits)
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("Double-entry violation"))
        }
    }

    @Nested
    @DisplayName("validateCurrencyConsistency")
    inner class ValidateCurrencyConsistency {

        @Test
        @DisplayName("should pass when all accounts match transaction currency")
        fun allAccountsMatch() {
            val accounts = listOf(
                Account(id = UUID.randomUUID(), owner = owner, name = "A1", type = AccountType.ASSET, currency = eur),
                Account(id = UUID.randomUUID(), owner = owner, name = "A2", type = AccountType.EXPENSE, currency = eur),
            )
            assertDoesNotThrow { validator.validateCurrencyConsistency(accounts, "EUR") }
        }

        @Test
        @DisplayName("should reject when an account has different currency")
        fun rejectMismatch() {
            val accounts = listOf(
                Account(id = UUID.randomUUID(), owner = owner, name = "EUR Acct", type = AccountType.ASSET, currency = eur),
                Account(id = UUID.randomUUID(), owner = owner, name = "USD Acct", type = AccountType.ASSET, currency = usd),
            )
            val ex = assertThrows(DomainHttpException::class.java) {
                validator.validateCurrencyConsistency(accounts, "EUR")
            }
            assertEquals(400, ex.status.value())
            assertTrue(ex.message!!.contains("currency"))
        }
    }
}
