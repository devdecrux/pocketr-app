package com.decrux.pocketr_api.entities.dtos

data class CurrencyDto(
    val code: String,
    val minorUnit: Short,
    val name: String,
)
