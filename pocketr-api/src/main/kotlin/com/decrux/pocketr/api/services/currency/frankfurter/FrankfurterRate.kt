package com.decrux.pocketr.api.services.currency.frankfurter

import java.math.BigDecimal
import java.time.LocalDate

data class FrankfurterRate(
    val date: LocalDate,
    val base: String,
    val quote: String,
    val rate: BigDecimal,
)
