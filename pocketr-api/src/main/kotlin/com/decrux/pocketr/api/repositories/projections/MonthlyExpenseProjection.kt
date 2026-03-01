package com.decrux.pocketr.api.repositories.projections

import java.util.UUID

data class MonthlyExpenseProjection(
    val expenseAccountId: UUID,
    val expenseAccountName: String,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
    val currency: String,
    val netMinor: Long,
)
