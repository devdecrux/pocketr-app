package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.BalanceDto
import com.decrux.pocketr_api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr_api.entities.dtos.PagedTransactionsDto
import com.decrux.pocketr_api.entities.dtos.TransactionDto
import com.decrux.pocketr_api.services.ledger.ManageLedger
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/v1/ledger")
class LedgerController(
    private val manageLedger: ManageLedger,
) {

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransaction(
        @RequestBody dto: CreateTransactionDto,
        @AuthenticationPrincipal user: User,
    ): TransactionDto {
        return manageLedger.createTransaction(dto, user)
    }

    @GetMapping("/transactions")
    fun listTransactions(
        @AuthenticationPrincipal user: User,
        @RequestParam mode: String?,
        @RequestParam householdId: UUID?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam accountId: UUID?,
        @RequestParam categoryId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
    ): PagedTransactionsDto {
        return manageLedger.listTransactions(user, mode, householdId, dateFrom, dateTo, accountId, categoryId, page, size)
    }

    @GetMapping("/accounts/{id}/balance")
    fun getAccountBalance(
        @PathVariable id: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOf: LocalDate?,
        @RequestParam householdId: UUID?,
        @AuthenticationPrincipal user: User,
    ): BalanceDto {
        return manageLedger.getAccountBalance(id, asOf ?: LocalDate.now(), user, householdId)
    }

    @GetMapping("/accounts/balances")
    fun getAccountBalances(
        @RequestParam accountIds: List<UUID>,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOf: LocalDate?,
        @RequestParam householdId: UUID?,
        @AuthenticationPrincipal user: User,
    ): List<BalanceDto> {
        return manageLedger.getAccountBalances(accountIds, asOf ?: LocalDate.now(), user, householdId)
    }
}
