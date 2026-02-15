package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateSplitDto(
    val accountId: UUID,
    val side: String,
    val amountMinor: Long,
    val categoryTagId: UUID? = null,
    val memo: String? = null,
)

data class CreateTransactionDto(
    val mode: String? = null,
    val householdId: UUID? = null,
    val txnDate: LocalDate,
    val currency: String,
    val description: String,
    val splits: List<CreateSplitDto>,
)

data class SplitDto(
    val id: UUID,
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val side: String,
    val amountMinor: Long,
    val effectMinor: Long,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
    val memo: String?,
)

data class TransactionDto(
    val id: UUID,
    val txnDate: LocalDate,
    val currency: String,
    val description: String,
    val householdId: UUID?,
    val txnKind: String,
    val splits: List<SplitDto>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BalanceDto(
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val currency: String,
    val balanceMinor: Long,
    val asOf: LocalDate,
)
