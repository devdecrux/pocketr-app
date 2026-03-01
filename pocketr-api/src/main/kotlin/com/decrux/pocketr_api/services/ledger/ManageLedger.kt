package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.BalanceDto
import com.decrux.pocketr_api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr_api.entities.dtos.PagedTransactionsDto
import com.decrux.pocketr_api.entities.dtos.TransactionDto
import java.time.LocalDate
import java.util.UUID

interface ManageLedger {
    fun createTransaction(
        dto: CreateTransactionDto,
        creator: User,
    ): TransactionDto

    fun listTransactions(
        user: User,
        mode: String?,
        householdId: UUID?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        accountId: UUID?,
        categoryId: UUID?,
        page: Int,
        size: Int,
    ): PagedTransactionsDto

    fun getAccountBalances(
        accountIds: List<UUID>,
        asOf: LocalDate,
        user: User,
        householdId: UUID?,
    ): List<BalanceDto>

    fun getAccountBalance(
        accountId: UUID,
        asOf: LocalDate,
        user: User,
        householdId: UUID?,
    ): BalanceDto
}
