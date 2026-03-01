package com.decrux.pocketr.api.repositories.projections

import java.util.UUID

data class AccountRawBalanceProjection(
    val accountId: UUID,
    val rawBalance: Long,
)
