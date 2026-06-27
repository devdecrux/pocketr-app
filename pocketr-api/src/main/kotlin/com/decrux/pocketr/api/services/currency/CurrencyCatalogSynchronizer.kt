package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class CurrencyCatalogSynchronizer(
    private val frankfurterClient: FrankfurterClient,
    private val currencyRepository: CurrencyRepository,
) {
    @Transactional
    fun synchronize() {
        val syncedCurrencies = frankfurterClient.fetchCurrencies()
        if (syncedCurrencies.isEmpty()) {
            logger.warn("Frankfurter returned no currencies; keeping existing catalog.")
            return
        }

        val existingByCode = currencyRepository.findAllById(syncedCurrencies.map { it.code }).associateBy { it.code }
        val entities =
            syncedCurrencies.map { synced ->
                val entity = existingByCode[synced.code] ?: Currency(code = synced.code)
                entity.iso = synced.isoNumeric
                entity.minorUnit = minorUnitFor(synced.code)
                entity.name = synced.name
                entity.symbol = synced.symbol
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
