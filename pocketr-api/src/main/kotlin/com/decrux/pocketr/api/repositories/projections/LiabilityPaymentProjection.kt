package com.decrux.pocketr.api.repositories.projections

import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import java.util.UUID

data class LiabilityPaymentProjection(
    val liabilityAccountId: UUID,
    val liabilityAccountName: String,
    val currency: String,
    val netMinor: Long,
    val liabilityAccountStatus: AccountStatus = AccountStatus.ACTIVE,
)
