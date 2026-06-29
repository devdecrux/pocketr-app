package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import com.decrux.pocketr.api.repositories.CurrencyExchangeRateRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Slf4j
class CurrencyExchangeRateSynchronizer(
    private val frankfurterClient: FrankfurterClient,
    private val currencyRepository: CurrencyRepository,
    private val exchangeRateRepository: CurrencyExchangeRateRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Transactional
    fun update(baseCurrency: String) {
        val normalizedBase = baseCurrency.uppercase()
        val newRates = frankfurterClient.fetchRates(normalizedBase)
        if (newRates.isEmpty()) {
            logger.warn("Frankfurter returned no exchange rates for {}; keeping existing rates.", normalizedBase)
            return
        }
        val mismatchedBaseRates = newRates.filter { it.base != normalizedBase }
        if (mismatchedBaseRates.isNotEmpty()) {
            val mismatchedBases = mismatchedBaseRates.map { it.base }.distinct()
            throw IllegalStateException("Frankfurter returned rates for unexpected base currencies: $mismatchedBases")
        }

        val base =
            currencyRepository
                .findById(normalizedBase)
                .orElseThrow { IllegalStateException("Base currency $normalizedBase is missing") }
        val newQuoteCodes = newRates.map { it.quote }.distinct()
        val storedQuoteCodes = currencyRepository.findAllById(newQuoteCodes).associateBy { it.code }
        val missingQuoteCodes = newQuoteCodes.filter { it !in storedQuoteCodes }
        if (missingQuoteCodes.isNotEmpty()) {
            logger.warn("Quote currencies are missing: $missingQuoteCodes")
        }

        val updatedAt = Instant.now(clock)
        val entities =
            newRates.map { synced ->
                val quote = requireNotNull(storedQuoteCodes[synced.quote])
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
