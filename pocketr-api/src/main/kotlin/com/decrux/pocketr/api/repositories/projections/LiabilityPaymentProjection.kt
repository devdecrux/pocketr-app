package com.decrux.pocketr.api.repositories.projections

import java.util.*

data class LiabilityPaymentProjection(
    val liabilityAccountId: UUID,
    val liabilityAccountName: String,
    val currency: String,
    val netMinor: Long,
)
