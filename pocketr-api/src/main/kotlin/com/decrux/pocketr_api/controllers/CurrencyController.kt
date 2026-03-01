package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.ledger.Currency
import com.decrux.pocketr_api.entities.dtos.CurrencyDto
import com.decrux.pocketr_api.repositories.CurrencyRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/currencies")
class CurrencyController(
    private val currencyRepository: CurrencyRepository,
) {
    @GetMapping
    fun listCurrencies(): List<CurrencyDto> = currencyRepository.findAll().map { it.toDto() }

    private fun Currency.toDto() =
        CurrencyDto(
            code = code,
            minorUnit = minorUnit,
            name = name,
        )
}
