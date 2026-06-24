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
        val mismatchedBaseRates = syncedRates.filter { it.base != normalizedBase }
        if (mismatchedBaseRates.isNotEmpty()) {
            val mismatchedBases = mismatchedBaseRates.map { it.base }.distinct()
            throw IllegalStateException("Frankfurter returned rates for unexpected base currencies: $mismatchedBases")
        }

        val base =
            currencyRepository
                .findById(normalizedBase)
                .orElseThrow { IllegalStateException("Base currency $normalizedBase is missing") }
        val quoteCodes = syncedRates.map { it.quote }.distinct()
        val quotesByCode = currencyRepository.findAllById(quoteCodes).associateBy { it.code }
        val missingQuoteCodes = quoteCodes.filter { it !in quotesByCode }
        if (missingQuoteCodes.isNotEmpty()) {
            throw IllegalStateException("Quote currencies are missing: $missingQuoteCodes")
        }

        val updatedAt = Instant.now(clock)
        val entities =
            syncedRates.map { synced ->
                val quote = requireNotNull(quotesByCode[synced.quote])
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
