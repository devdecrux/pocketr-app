package com.decrux.pocketr_api.entities.dtos

import java.time.LocalDate
import java.util.UUID

data class CreateTransactionDto(
    val mode: String? = null,
    val householdId: UUID? = null,
    val txnDate: LocalDate,
    val currency: String,
    val description: String,
    val splits: List<CreateSplitDto>,
)
