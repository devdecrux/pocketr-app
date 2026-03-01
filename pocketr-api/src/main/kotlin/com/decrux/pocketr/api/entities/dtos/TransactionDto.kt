package com.decrux.pocketr.api.entities.dtos

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class TransactionDto(
    val id: UUID,
    val txnDate: LocalDate,
    val currency: String,
    val description: String,
    val householdId: UUID?,
    val txnKind: String,
    val createdBy: TxnCreatorDto?,
    val splits: List<SplitDto>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
