package com.decrux.pocketr.api.entities.dtos

import java.time.LocalDate

data class BalanceTimeseriesPointDto(
    val date: LocalDate,
    val balanceMinor: Long,
)
