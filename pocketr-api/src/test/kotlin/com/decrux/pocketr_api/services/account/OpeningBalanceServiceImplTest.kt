package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.dtos.BalanceDto
import com.decrux.pocketr_api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr_api.entities.dtos.PagedTransactionsDto
import com.decrux.pocketr_api.entities.dtos.TransactionDto
import com.decrux.pocketr_api.exceptions.DomainBadRequestException
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.UserRepository
import com.decrux.pocketr_api.services.OwnershipGuard
import com.decrux.pocketr_api.services.ledger.ManageLedger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("OpeningBalanceServiceImpl")
class OpeningBalanceServiceImplTest {

    private val accountRepository = mock(AccountRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val manageLedger = CapturingManageLedger()

    private val service = OpeningBalanceServiceImpl(
        accountRepository = accountRepository,
        userRepository = userRepository,
        manageLedger = manageLedger,
        ownershipGuard = OwnershipGuard(),
    )

    private val owner = User(
        userId = 1L,
        passwordValue = "encoded",
        email = "owner@example.com",
    )
    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")

    @Test
    @DisplayName("creates positive opening balance as DEBIT(asset)/CREDIT(equity)")
    fun createsPositiveOpeningBalance() {
        val assetAccount = Account(
            id = UUID.randomUUID(),
            owner = owner,
            name = "Checking",
            type = AccountType.ASSET,
            currency = eur,
        )
        val equityAccountId = UUID.randomUUID()

        `when`(userRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(owner))
        `when`(
            accountRepository.findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
                1L,
                AccountType.EQUITY,
                "EUR",
                "Opening Equity",
            ),
        ).thenReturn(null)
        `when`(accountRepository.save(any(Account::class.java))).thenAnswer { invocation ->
            val saved = invocation.getArgument<Account>(0)
            saved.id = equityAccountId
            saved
        }

        service.createForNewAssetAccount(owner, assetAccount, 100_000, LocalDate.parse("2026-02-15"))

        assertEquals(1, manageLedger.calls.size)
        val call = manageLedger.calls.single()
        val dto = call.dto

        assertEquals("INDIVIDUAL", dto.mode)
        assertEquals(owner.userId, call.creator.userId)
        assertEquals("EUR", dto.currency)
        assertEquals(2, dto.splits.size)
        assertEquals(assetAccount.id, dto.splits[0].accountId)
        assertEquals("DEBIT", dto.splits[0].side)
        assertEquals(100_000, dto.splits[0].amountMinor)
        assertEquals(equityAccountId, dto.splits[1].accountId)
        assertEquals("CREDIT", dto.splits[1].side)
        assertEquals(100_000, dto.splits[1].amountMinor)
    }

    @Test
    @DisplayName("creates negative opening balance as CREDIT(asset)/DEBIT(equity)")
    fun createsNegativeOpeningBalance() {
        val assetAccount = Account(
            id = UUID.randomUUID(),
            owner = owner,
            name = "Checking",
            type = AccountType.ASSET,
            currency = eur,
        )
        val equityAccount = Account(
            id = UUID.randomUUID(),
            owner = owner,
            name = "Opening Equity",
            type = AccountType.EQUITY,
            currency = eur,
        )

        `when`(userRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(owner))
        `when`(
            accountRepository.findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
                1L,
                AccountType.EQUITY,
                "EUR",
                "Opening Equity",
            ),
        ).thenReturn(equityAccount)

        service.createForNewAssetAccount(owner, assetAccount, -5000, LocalDate.parse("2026-02-15"))

        assertEquals(1, manageLedger.calls.size)
        val call = manageLedger.calls.single()
        val dto = call.dto

        assertEquals(2, dto.splits.size)
        assertEquals(owner.userId, call.creator.userId)
        assertEquals("CREDIT", dto.splits[0].side)
        assertEquals(5000, dto.splits[0].amountMinor)
        assertEquals("DEBIT", dto.splits[1].side)
        assertEquals(5000, dto.splits[1].amountMinor)
        verify(accountRepository, never()).save(any(Account::class.java))
    }

    @Test
    @DisplayName("rejects opening balance for non-ASSET account")
    fun rejectsNonAsset() {
        val liability = Account(
            id = UUID.randomUUID(),
            owner = owner,
            name = "Loan",
            type = AccountType.LIABILITY,
            currency = eur,
        )

        val ex = assertThrows(DomainBadRequestException::class.java) {
            service.createForNewAssetAccount(owner, liability, 1000, LocalDate.parse("2026-02-15"))
        }

        assertEquals(400, ex.status.value())
        verifyNoInteractions(userRepository)
        assertEquals(0, manageLedger.calls.size)
    }

    @Test
    @DisplayName("rejects opening balance when caller does not own account")
    fun rejectsNonOwner() {
        val anotherUser = User(
            userId = 2L,
            passwordValue = "encoded",
            email = "another@example.com",
        )
        val assetAccount = Account(
            id = UUID.randomUUID(),
            owner = anotherUser,
            name = "Checking",
            type = AccountType.ASSET,
            currency = eur,
        )

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.createForNewAssetAccount(owner, assetAccount, 1000, LocalDate.parse("2026-02-15"))
        }

        assertEquals(403, ex.statusCode.value())
        verifyNoInteractions(userRepository)
        assertEquals(0, manageLedger.calls.size)
    }

    private data class LedgerCall(
        val dto: CreateTransactionDto,
        val creator: User,
    )

    private class CapturingManageLedger : ManageLedger {
        val calls = mutableListOf<LedgerCall>()

        override fun createTransaction(dto: CreateTransactionDto, creator: User): TransactionDto {
            calls.add(LedgerCall(dto = dto, creator = creator))
            return TransactionDto(
                id = UUID.randomUUID(),
                txnDate = dto.txnDate,
                currency = dto.currency,
                description = dto.description,
                householdId = dto.householdId,
                txnKind = "TRANSFER",
                createdBy = null,
                splits = emptyList(),
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            )
        }

        override fun listTransactions(
            user: User,
            mode: String?,
            householdId: UUID?,
            dateFrom: LocalDate?,
            dateTo: LocalDate?,
            accountId: UUID?,
            categoryId: UUID?,
            page: Int,
            size: Int,
        ): PagedTransactionsDto = PagedTransactionsDto(content = emptyList(), page = 0, size = 50, totalElements = 0, totalPages = 0)

        override fun getAccountBalance(
            accountId: UUID,
            asOf: LocalDate,
            user: User,
            householdId: UUID?,
        ): BalanceDto {
            throw UnsupportedOperationException("Not used in this test")
        }
    }
}
