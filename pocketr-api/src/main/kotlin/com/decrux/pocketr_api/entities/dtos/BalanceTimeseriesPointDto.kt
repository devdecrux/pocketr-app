package com.decrux.pocketr_api.entities.dtos

import java.time.LocalDate

data class BalanceTimeseriesPointDto(
    val date: LocalDate,
    val balanceMinor: Long,
)
