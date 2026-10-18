package com.decrux.pocketr.api.services.ledger

import java.time.LocalDate
import java.util.UUID

data class TransactionListFilter(
    val mode: String? = null,
    val householdId: UUID? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val accountId: UUID? = null,
    val categoryId: UUID? = null,
    val spendingOnly: Boolean = false,
)
