package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.repositories.CurrencyExchangeRateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.Optional

@DisplayName("CurrencyConversionService")
class CurrencyConversionServiceTest {
    private lateinit var exchangeRateRepository: CurrencyExchangeRateRepository
    private lateinit var service: CurrencyConversionService

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro")
    private val usd = Currency(code = "USD", minorUnit = 2, name = "US Dollar")
    private val jpy = Currency(code = "JPY", minorUnit = 0, name = "Japanese Yen")
    private val bgn = Currency(code = "BGN", minorUnit = 2, name = "Bulgarian Lev")

    @BeforeEach
    fun setUp() {
        exchangeRateRepository = mock(CurrencyExchangeRateRepository::class.java)
        service = CurrencyConversionService(exchangeRateRepository)
    }

    @Test
    fun sameCurrencyUsesRateOne() {
        val result =
            service.convert(
                sourceCurrency = eur,
                targetCurrency = eur,
                baseCurrencyCode = "EUR",
                amountMinor = 1234,
            )

        assertEquals(1234, result.amountMinor)
        assertEquals(BigDecimal("1"), result.exchangeRate)
    }

    @Test
    fun directBaseRateConvertsMinorUnits() {
        stubRate("EUR", "JPY", "162.50")

        val result =
            service.convert(
                sourceCurrency = eur,
                targetCurrency = jpy,
                baseCurrencyCode = "EUR",
                amountMinor = 1234,
            )

        assertEquals(2005, result.amountMinor)
        assertEquals(BigDecimal("162.5"), result.exchangeRate)
    }

    @Test
    fun inverseRateConvertsToBaseCurrency() {
        stubRate("EUR", "USD", "1.25")

        val result =
            service.convert(
                sourceCurrency = usd,
                targetCurrency = eur,
                baseCurrencyCode = "EUR",
                amountMinor = 1250,
            )

        assertEquals(1000, result.amountMinor)
        assertEquals(BigDecimal("0.8"), result.exchangeRate)
    }

    @Test
    fun crossRateConvertsBetweenTwoQuotes() {
        stubRate("EUR", "USD", "1.25")
        stubRate("EUR", "BGN", "1.95583")

        val result =
            service.convert(
                sourceCurrency = usd,
                targetCurrency = bgn,
                baseCurrencyCode = "EUR",
                amountMinor = 1250,
            )

        assertEquals(1956, result.amountMinor)
        assertEquals(BigDecimal("1.564664"), result.exchangeRate)
    }

    @Test
    fun missingRateFailsClearly() {
        assertThrows(BadRequestException::class.java) {
            service.convert(
                sourceCurrency = usd,
                targetCurrency = eur,
                baseCurrencyCode = "EUR",
                amountMinor = 1000,
            )
        }
    }

    private fun stubRate(
        base: String,
        quote: String,
        rate: String,
    ) {
        `when`(exchangeRateRepository.findById(CurrencyExchangeRateId(base, quote)))
            .thenReturn(
                Optional.of(
                    CurrencyExchangeRate(
                        id = CurrencyExchangeRateId(base, quote),
                        rate = BigDecimal(rate),
                    ),
                ),
            )
    }
}
