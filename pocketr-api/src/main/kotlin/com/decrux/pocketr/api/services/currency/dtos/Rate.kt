package com.decrux.pocketr.api.services.currency.dtos

import java.math.BigDecimal
import java.time.LocalDate

data class Rate(
    val date: LocalDate,
    val base: String,
    val quote: String,
    val rate: BigDecimal,
)
