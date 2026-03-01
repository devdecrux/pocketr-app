package com.decrux.pocketr_api.entities.dtos

import java.util.UUID

data class MonthlyExpenseDto(
    val expenseAccountId: UUID,
    val expenseAccountName: String,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
    val currency: String,
    val netMinor: Long,
)
