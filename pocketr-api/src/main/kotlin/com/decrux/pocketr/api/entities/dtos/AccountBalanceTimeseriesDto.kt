package com.decrux.pocketr.api.entities.dtos

import java.util.UUID

data class AccountBalanceTimeseriesDto(
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val currency: String,
    val points: List<BalanceTimeseriesPointDto>,
)
