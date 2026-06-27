package com.decrux.pocketr.api.services.currency.frankfurter

import com.decrux.pocketr.api.services.currency.dtos.Currency
import com.decrux.pocketr.api.services.currency.dtos.Rate
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate

@Component
class FrankfurterClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${pocketr.currency.frankfurter.base-url:https://api.frankfurter.dev}")
    baseUrl: String,
) {
    private val restClient =
        restClientBuilder
            .baseUrl(baseUrl.trimEnd('/'))
            .build()

    fun fetchCurrencies(): List<Currency> =
        (
            restClient
                .get()
                .uri("/v2/currencies")
                .retrieve()
                .body(Array<FrankfurterCurrencyResponse>::class.java)
                ?: emptyArray()
        ).map { it.toDomain() }

    fun fetchRates(baseCurrency: String): List<Rate> =
        (
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/v2/rates")
                        .queryParam("base", baseCurrency.uppercase())
                        .build()
                }.retrieve()
                .body(Array<FrankfurterRateResponse>::class.java)
                ?: emptyArray()
        ).map { it.toDomain() }
}

private data class FrankfurterCurrencyResponse(
    @JsonProperty("iso_code")
    val isoCode: String = "",
    val name: String = "",
    val symbol: String? = null,
) {
    fun toDomain(): Currency {
        require(isoCode.isNotBlank()) { "Missing Frankfurter field: iso_code" }
        require(name.isNotBlank()) { "Missing Frankfurter field: name" }
        require(!symbol.isNullOrBlank()) { "Missing Frankfurter field: symbol" }

        val code = isoCode.uppercase()
        return Currency(
            code = code,
            name = name,
            symbol = symbol,
        )
    }
}

private data class FrankfurterRateResponse(
    val date: String = "",
    val base: String = "",
    val quote: String = "",
    val rate: BigDecimal = BigDecimal.ZERO,
) {
    fun toDomain(): Rate {
        require(date.isNotBlank()) { "Missing Frankfurter field: date" }
        require(base.isNotBlank()) { "Missing Frankfurter field: base" }
        require(quote.isNotBlank()) { "Missing Frankfurter field: quote" }
        require(rate > BigDecimal.ZERO) { "Invalid Frankfurter field: rate" }

        return Rate(
            date = LocalDate.parse(date),
            base = base.uppercase(),
            quote = quote.uppercase(),
            rate = rate,
        )
    }
}
