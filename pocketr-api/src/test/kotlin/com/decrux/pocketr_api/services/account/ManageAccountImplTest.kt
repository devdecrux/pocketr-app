package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.CurrencyRepository
import com.decrux.pocketr_api.repositories.HouseholdAccountShareRepository
import com.decrux.pocketr_api.services.OwnershipGuard
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("ManageAccountImpl")
class ManageAccountImplTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var currencyRepository: CurrencyRepository
    private lateinit var openingBalanceService: CapturingOpeningBalanceService
    private lateinit var manageHousehold: ManageHousehold
    private lateinit var householdAccountShareRepository: HouseholdAccountShareRepository
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
        openingBalanceService = CapturingOpeningBalanceService()
        manageHousehold = mock(ManageHousehold::class.java)
        householdAccountShareRepository = mock(HouseholdAccountShareRepository::class.java)
        service = ManageAccountImpl(
            accountRepository, currencyRepository, openingBalanceService,
            manageHousehold, householdAccountShareRepository, OwnershipGuard(),
        )

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
            assertTrue(openingBalanceService.calls.isEmpty())
        }

        @Test
        @DisplayName("should create accounts for all user-creatable types")
        fun createAccountForUserCreatableTypes() {
            val creatableTypes = listOf(AccountType.ASSET, AccountType.LIABILITY, AccountType.INCOME, AccountType.EXPENSE)
            for (type in creatableTypes) {
                val dto = CreateAccountDto(name = "Test ${type.name}", type = type.name, currency = "EUR")
                `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
                    val account = invocation.getArgument<Account>(0)
                    account.id = UUID.randomUUID()
                    account
                }

                val result = service.createAccount(dto, ownerUser)
                assertEquals(type.name, result.type)
            }
            assertTrue(openingBalanceService.calls.isEmpty())
        }

        @Test
        @DisplayName("should reject manual creation of EQUITY accounts")
        fun rejectManualEquityCreation() {
            val dto = CreateAccountDto(name = "Opening Equity", type = "EQUITY", currency = "EUR")

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createAccount(dto, ownerUser)
            }
            assertEquals(400, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("system-managed"))
            verify(accountRepository, never()).save(any(Account::class.java))
            assertTrue(openingBalanceService.calls.isEmpty())
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
            assertTrue(openingBalanceService.calls.isEmpty())
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
            assertTrue(openingBalanceService.calls.isEmpty())
        }

        @Test
        @DisplayName("should create opening balance transaction when openingBalanceMinor is non-zero")
        fun createOpeningBalanceWhenRequested() {
            val accountId = UUID.randomUUID()
            val date = LocalDate.parse("2026-02-15")
            val dto = CreateAccountDto(
                name = "Checking",
                type = "ASSET",
                currency = "EUR",
                openingBalanceMinor = 100_000,
                openingBalanceDate = date,
            )

            `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
                val account = invocation.getArgument<Account>(0)
                account.id = accountId
                account
            }

            val result = service.createAccount(dto, ownerUser)

            assertEquals(accountId, result.id)
            assertEquals(1, openingBalanceService.calls.size)
            val call = openingBalanceService.calls.single()
            assertEquals(ownerUser.userId, call.owner.userId)
            assertEquals(accountId, call.account.id)
            assertEquals(100_000L, call.amountMinor)
            assertEquals(date, call.txnDate)
        }

        @Test
        @DisplayName("should reject opening balance for non-ASSET account type")
        fun rejectOpeningBalanceForNonAssetType() {
            val dto = CreateAccountDto(
                name = "Salary",
                type = "INCOME",
                currency = "EUR",
                openingBalanceMinor = 50_000,
            )

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createAccount(dto, ownerUser)
            }

            assertEquals(400, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("ASSET"))
            verify(accountRepository, never()).save(any(Account::class.java))
            assertTrue(openingBalanceService.calls.isEmpty())
        }

        @Test
        @DisplayName("should reject openingBalanceDate when openingBalanceMinor is zero")
        fun rejectOpeningBalanceDateWithoutAmount() {
            val dto = CreateAccountDto(
                name = "Checking",
                type = "ASSET",
                currency = "EUR",
                openingBalanceDate = LocalDate.parse("2026-02-15"),
            )

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createAccount(dto, ownerUser)
            }

            assertEquals(400, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("openingBalanceDate"))
            verify(accountRepository, never()).save(any(Account::class.java))
            assertTrue(openingBalanceService.calls.isEmpty())
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

    @Nested
    @DisplayName("listAccounts with mode")
    inner class ListAccountsWithMode {

        private val householdId = UUID.randomUUID()

        @Test
        @DisplayName("INDIVIDUAL mode returns only owner accounts")
        fun individualModeReturnsOwnerAccounts() {
            val accounts = listOf(
                Account(id = UUID.randomUUID(), owner = ownerUser, name = "Checking", type = AccountType.ASSET, currency = eur),
            )
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(accounts)

            val result = service.listAccounts(ownerUser, "INDIVIDUAL", null)
            assertEquals(1, result.size)
            assertEquals("Checking", result[0].name)
            verify(accountRepository).findByOwnerUserId(1L)
            verifyNoInteractions(manageHousehold)
            verifyNoInteractions(householdAccountShareRepository)
        }

        @Test
        @DisplayName("HOUSEHOLD mode returns owned + shared accounts")
        fun householdModeReturnsOwnedAndSharedAccounts() {
            val ownedAccount = Account(id = UUID.randomUUID(), owner = ownerUser, name = "My Checking", type = AccountType.ASSET, currency = eur)
            val sharedAccount = Account(id = UUID.randomUUID(), owner = otherUser, name = "Bob Savings", type = AccountType.ASSET, currency = eur)

            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true)
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(listOf(ownedAccount))
            `when`(householdAccountShareRepository.findSharedAccountIdsByHouseholdId(householdId))
                .thenReturn(setOf(sharedAccount.id!!))
            doReturn(listOf(sharedAccount)).`when`(accountRepository).findAllById(any())

            val result = service.listAccounts(ownerUser, "HOUSEHOLD", householdId)
            assertEquals(2, result.size)
            val names = result.map { it.name }.toSet()
            assertTrue(names.contains("My Checking"))
            assertTrue(names.contains("Bob Savings"))
        }

        @Test
        @DisplayName("HOUSEHOLD mode deduplicates when owned account is also shared")
        fun householdModeDeduplicates() {
            val sharedOwnedAccount = Account(id = UUID.randomUUID(), owner = ownerUser, name = "Shared Checking", type = AccountType.ASSET, currency = eur)

            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true)
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(listOf(sharedOwnedAccount))
            `when`(householdAccountShareRepository.findSharedAccountIdsByHouseholdId(householdId))
                .thenReturn(setOf(sharedOwnedAccount.id!!))
            doReturn(listOf(sharedOwnedAccount)).`when`(accountRepository).findAllById(any())

            val result = service.listAccounts(ownerUser, "HOUSEHOLD", householdId)
            assertEquals(1, result.size)
            assertEquals("Shared Checking", result[0].name)
        }

        @Test
        @DisplayName("HOUSEHOLD mode with no shared accounts returns only owned")
        fun householdModeNoSharedAccounts() {
            val ownedAccount = Account(id = UUID.randomUUID(), owner = ownerUser, name = "Checking", type = AccountType.ASSET, currency = eur)

            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true)
            `when`(accountRepository.findByOwnerUserId(1L)).thenReturn(listOf(ownedAccount))
            `when`(householdAccountShareRepository.findSharedAccountIdsByHouseholdId(householdId))
                .thenReturn(emptySet())

            val result = service.listAccounts(ownerUser, "HOUSEHOLD", householdId)
            assertEquals(1, result.size)
            assertEquals("Checking", result[0].name)
        }

        @Test
        @DisplayName("HOUSEHOLD mode rejects non-member with 403")
        fun householdModeRejectsNonMember() {
            `when`(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(false)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.listAccounts(ownerUser, "HOUSEHOLD", householdId)
            }
            assertEquals(403, ex.statusCode.value())
            verify(accountRepository, never()).findByOwnerUserId(anyLong())
        }

        @Test
        @DisplayName("HOUSEHOLD mode without householdId returns 400")
        fun householdModeWithoutHouseholdId() {
            val ex = assertThrows(ResponseStatusException::class.java) {
                service.listAccounts(ownerUser, "HOUSEHOLD", null)
            }
            assertEquals(400, ex.statusCode.value())
        }

        @Test
        @DisplayName("Invalid mode returns 400")
        fun invalidModeReturns400() {
            val ex = assertThrows(ResponseStatusException::class.java) {
                service.listAccounts(ownerUser, "INVALID", null)
            }
            assertEquals(400, ex.statusCode.value())
        }
    }

    private data class OpeningBalanceCall(
        val owner: User,
        val account: Account,
        val amountMinor: Long,
        val txnDate: LocalDate,
    )

    private class CapturingOpeningBalanceService : OpeningBalanceService {
        val calls = mutableListOf<OpeningBalanceCall>()

        override fun createForNewAssetAccount(
            owner: User,
            assetAccount: Account,
            openingBalanceMinor: Long,
            txnDate: LocalDate,
        ) {
            calls.add(
                OpeningBalanceCall(
                    owner = owner,
                    account = assetAccount,
                    amountMinor = openingBalanceMinor,
                    txnDate = txnDate,
                ),
            )
        }
    }
}
