package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.repositories.CurrencyExchangeRateRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

data class CurrencyConversionResult(
    val amountMinor: Long,
    val exchangeRate: BigDecimal,
)

@Service
class CurrencyConversionService(
    private val exchangeRateRepository: CurrencyExchangeRateRepository,
) {
    fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        baseCurrencyCode: String,
        amountMinor: Long,
    ): CurrencyConversionResult {
        val sourceCode = requireNotNull(sourceCurrency.code)
        val targetCode = requireNotNull(targetCurrency.code)
        val rate = resolveRate(sourceCode, targetCode, baseCurrencyCode.uppercase())
        val sourceMajorAmount = toMajorAmount(amountMinor, sourceCurrency)
        val targetMajorAmount = sourceMajorAmount.multiply(rate)

        return CurrencyConversionResult(
            amountMinor = toMinorAmount(targetMajorAmount, targetCurrency),
            exchangeRate = rate,
        )
    }

    private fun resolveRate(
        sourceCurrencyCode: String,
        targetCurrencyCode: String,
        baseCurrencyCode: String,
    ): BigDecimal {
        val source = sourceCurrencyCode.uppercase()
        val target = targetCurrencyCode.uppercase()
        val base = baseCurrencyCode.uppercase()

        return when {
            source == target -> BigDecimal.ONE
            source == base -> baseRate(base, target)
            target == base -> BigDecimal.ONE.divide(baseRate(base, source), RATE_SCALE, RoundingMode.HALF_UP)
            else -> baseRate(base, target).divide(baseRate(base, source), RATE_SCALE, RoundingMode.HALF_UP)
        }.stripTrailingZeros()
    }

    private fun baseRate(
        baseCurrencyCode: String,
        quoteCurrencyCode: String,
    ): BigDecimal =
        exchangeRateRepository
            .findById(CurrencyExchangeRateId(baseCurrencyCode, quoteCurrencyCode))
            .map { it.rate }
            .orElseThrow { BadRequestException("Missing exchange rate $baseCurrencyCode->$quoteCurrencyCode") }

    private fun toMajorAmount(
        amountMinor: Long,
        currency: Currency,
    ): BigDecimal = BigDecimal.valueOf(amountMinor).movePointLeft(currency.minorUnit.toInt())

    private fun toMinorAmount(
        amountMajor: BigDecimal,
        currency: Currency,
    ): Long =
        amountMajor
            .movePointRight(currency.minorUnit.toInt())
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    private companion object {
        const val RATE_SCALE = 18
    }
}
