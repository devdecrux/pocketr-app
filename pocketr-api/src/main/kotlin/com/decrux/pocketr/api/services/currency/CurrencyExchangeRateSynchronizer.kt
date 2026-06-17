package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import com.decrux.pocketr.api.repositories.CurrencyExchangeRateRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class CurrencyExchangeRateSynchronizer(
    private val frankfurterClient: FrankfurterClient,
    private val currencyRepository: CurrencyRepository,
    private val exchangeRateRepository: CurrencyExchangeRateRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Transactional
    fun synchronize(baseCurrency: String) {
        val normalizedBase = baseCurrency.uppercase()
        val syncedRates = frankfurterClient.fetchRates(normalizedBase)
        if (syncedRates.isEmpty()) {
            logger.warn("Frankfurter returned no exchange rates for {}; keeping existing rates.", normalizedBase)
            return
        }

        val base =
            currencyRepository
                .findById(normalizedBase)
                .orElseThrow { IllegalStateException("Base currency $normalizedBase is missing") }
        val quotesByCode = currencyRepository.findAllById(syncedRates.map { it.quote }).associateBy { it.code }
        val updatedAt = Instant.now(clock)
        val entities =
            syncedRates.map { synced ->
                val quote = quotesByCode[synced.quote] ?: throw IllegalStateException("Quote currency ${synced.quote} is missing")
                CurrencyExchangeRate(
                    id = CurrencyExchangeRateId(normalizedBase, synced.quote),
                    base = base,
                    quote = quote,
                    rate = synced.rate,
                    providerDate = synced.date,
                    updatedAt = updatedAt,
                )
            }

        exchangeRateRepository.deleteByIdBaseCurrency(normalizedBase)
        exchangeRateRepository.saveAll(entities)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CurrencyExchangeRateSynchronizer::class.java)
    }
}
