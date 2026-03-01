package com.decrux.pocketr_api.entities.dtos

import java.time.LocalDate
import java.util.UUID

data class BalanceDto(
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val currency: String,
    val balanceMinor: Long,
    val asOf: LocalDate,
)
