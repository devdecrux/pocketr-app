package com.decrux.pocketr.api.repositories.projections

import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import java.util.UUID

data class MonthlyExpenseProjection(
    val expenseAccountId: UUID,
    val expenseAccountName: String,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
    val currency: String,
    val netMinor: Long,
    val categoryTagColor: String? = null,
    val expenseAccountStatus: AccountStatus = AccountStatus.ACTIVE,
)
