package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

@Service
class CurrencyCatalogSynchronizer(
    private val frankfurterClient: FrankfurterClient,
    private val currencyRepository: CurrencyRepository,
) {
    @Transactional
    fun update() {
        val newCurrencies = frankfurterClient.fetchCurrencies()
        if (newCurrencies.isEmpty()) {
            logger.warn("Frankfurter returned no currencies; keeping existing catalog.")
            return
        }

        val existingByCode = currencyRepository.findAllById(newCurrencies.map { it.code }).associateBy { it.code }
        val entities =
            newCurrencies.map { newCurrency ->
                val entity = existingByCode[newCurrency.code] ?: Currency(code = newCurrency.code)
                entity.minorUnit = minorUnitFor(newCurrency.code)
                entity.name = newCurrency.name
                entity.symbol = newCurrency.symbol
                entity
            }

        currencyRepository.saveAll(entities)
    }

    private fun minorUnitFor(code: String): Short =
        try {
            val digits =
                java.util.Currency
                    .getInstance(code.uppercase(Locale.ROOT))
                    .defaultFractionDigits
            if (digits < 0) 2 else digits.toShort()
        } catch (_: IllegalArgumentException) {
            2
        }

    private companion object {
        private val logger = LoggerFactory.getLogger(CurrencyCatalogSynchronizer::class.java)
    }
}
