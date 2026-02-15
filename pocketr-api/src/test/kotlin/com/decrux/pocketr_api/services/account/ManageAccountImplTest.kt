package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.CurrencyRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

@DisplayName("ManageAccountImpl")
class ManageAccountImplTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var currencyRepository: CurrencyRepository
    private lateinit var service: ManageAccountImpl

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")
    private val usd = Currency(code = "USD", minorUnit = 2, name = "US Dollar")

    private val ownerUser = User(
        userId = 1L,
        passwordValue = "encoded",
        email = "alice@example.com",
    )

    private val otherUser = User(
        userId = 2L,
        passwordValue = "encoded",
        email = "bob@example.com",
    )

    @BeforeEach
    fun setUp() {
        accountRepository = mock(AccountRepository::class.java)
        currencyRepository = mock(CurrencyRepository::class.java)
        service = ManageAccountImpl(accountRepository, currencyRepository)

        `when`(currencyRepository.findById("EUR")).thenReturn(Optional.of(eur))
        `when`(currencyRepository.findById("USD")).thenReturn(Optional.of(usd))
    }

    @Nested
    @DisplayName("createAccount")
    inner class CreateAccount {

        @Test
        @DisplayName("should create ASSET account with valid inputs")
        fun createAssetAccount() {
            val dto = CreateAccountDto(name = "Checking", type = "ASSET", currency = "EUR")
            val savedId = UUID.randomUUID()

            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
                val account = invocation.getArgument<Account>(0)
                account.id = savedId
                account
            }

            val result = service.createAccount(dto, ownerUser)

            assertEquals(savedId, result.id)
            assertEquals(1L, result.ownerUserId)
            assertEquals("Checking", result.name)
            assertEquals("ASSET", result.type)
            assertEquals("EUR", result.currency)
        }

        @Test
        @DisplayName("should create accounts for all valid types")
        fun createAccountForAllTypes() {
            for (type in AccountType.entries) {
                val dto = CreateAccountDto(name = "Test ${type.name}", type = type.name, currency = "EUR")
                `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
                    val account = invocation.getArgument<Account>(0)
                    account.id = UUID.randomUUID()
                    account
                }

                val result = service.createAccount(dto, ownerUser)
                assertEquals(type.name, result.type)
            }
        }

        @Test
        @DisplayName("should reject invalid account type")
        fun rejectInvalidAccountType() {
            val dto = CreateAccountDto(name = "Bad", type = "INVALID_TYPE", currency = "EUR")

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createAccount(dto, ownerUser)
            }
            assertEquals(400, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("Invalid account type"))
        }

        @Test
        @DisplayName("should reject unknown currency code")
        fun rejectUnknownCurrency() {
            `when`(currencyRepository.findById("XYZ")).thenReturn(Optional.empty())
            val dto = CreateAccountDto(name = "Checking", type = "ASSET", currency = "XYZ")

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createAccount(dto, ownerUser)
            }
            assertEquals(400, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("Invalid currency"))
        }

        @Test
        @DisplayName("should trim whitespace from account name")
        fun trimAccountName() {
            val dto = CreateAccountDto(name = "  Checking  ", type = "ASSET", currency = "EUR")
            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
                val account = invocation.getArgument<Account>(0)
                account.id = UUID.randomUUID()
                account
            }

            val result = service.createAccount(dto, ownerUser)
            assertEquals("Checking", result.name)
        }

        @Test
        @DisplayName("should set owner to the authenticated user")
        fun setOwnerToAuthenticatedUser() {
            val dto = CreateAccountDto(name = "Checking", type = "ASSET", currency = "EUR")
            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
                val account = invocation.getArgument<Account>(0)
                account.id = UUID.randomUUID()
                assertEquals(ownerUser.userId, account.owner?.userId)
                account
            }

            service.createAccount(dto, ownerUser)
            verify(accountRepository).save(any(Account::class.java))
        }
    }

    @Nested
    @DisplayName("listAccounts")
    inner class ListAccounts {

        @Test
        @DisplayName("should return owner accounts")
        fun returnOwnerAccounts() {
            val accounts = listOf(
                Account(
                    id = UUID.randomUUID(), owner = ownerUser,
                    name = "Checking", type = AccountType.ASSET, currency = eur,
                ),
                Account(
                    id = UUID.randomUUID(), owner = ownerUser,
                    name = "Savings", type = AccountType.ASSET, currency = eur,
                ),
            )
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(accounts)

            val result = service.listAccounts(ownerUser)
            assertEquals(2, result.size)
            assertEquals(1L, result[0].ownerUserId)
            assertEquals(1L, result[1].ownerUserId)
            assertEquals("Checking", result[0].name)
            assertEquals("Savings", result[1].name)
            verify(accountRepository).findByOwnerUserId(1L)
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        fun returnEmptyListWhenNoAccounts() {
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(emptyList())

            val result = service.listAccounts(ownerUser)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("updateAccount")
    inner class UpdateAccount {

        private val accountId = UUID.randomUUID()
        private val existingAccount = Account(
            id = accountId, owner = ownerUser,
            name = "Checking", type = AccountType.ASSET, currency = eur,
        )

        @Test
        @DisplayName("should rename account")
        fun renameAccount() {
            `when`(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount))
            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { it.getArgument<Account>(0) }

            val result = service.updateAccount(accountId, UpdateAccountDto(name = "Main Checking"), ownerUser)
            assertEquals("Main Checking", result.name)
        }

        @Test
        @DisplayName("should reject update by non-owner")
        fun rejectUpdateByNonOwner() {
            `when`(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount))

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.updateAccount(accountId, UpdateAccountDto(name = "Stolen"), otherUser)
            }
            assertEquals(403, ex.statusCode.value())
        }

        @Test
        @DisplayName("should return 404 for non-existent account")
        fun notFoundForMissingAccount() {
            val missingId = UUID.randomUUID()
            `when`(accountRepository.findById(missingId)).thenReturn(Optional.empty())

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.updateAccount(missingId, UpdateAccountDto(name = "X"), ownerUser)
            }
            assertEquals(404, ex.statusCode.value())
        }

        @Test
        @DisplayName("should trim whitespace when renaming")
        fun trimWhitespaceOnRename() {
            `when`(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount))
            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { it.getArgument<Account>(0) }

            val result = service.updateAccount(accountId, UpdateAccountDto(name = "  Savings  "), ownerUser)
            assertEquals("Savings", result.name)
        }

        @Test
        @DisplayName("should not change fields when update dto has nulls")
        fun noChangeWhenDtoFieldsNull() {
            `when`(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount))
            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { it.getArgument<Account>(0) }

            val result = service.updateAccount(accountId, UpdateAccountDto(), ownerUser)
            assertEquals("Checking", result.name)
            assertEquals("ASSET", result.type)
        }
    }
}
