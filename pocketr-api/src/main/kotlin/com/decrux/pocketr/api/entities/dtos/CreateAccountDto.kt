package com.decrux.pocketr.api.entities.dtos

import java.time.LocalDate

data class CreateAccountDto(
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinor: Long? = null,
    val openingBalanceDate: LocalDate? = null,
)
