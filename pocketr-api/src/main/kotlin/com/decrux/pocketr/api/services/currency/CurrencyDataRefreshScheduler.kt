package com.decrux.pocketr.api.services.currency

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Order(1)
class CurrencyDataRefreshScheduler(
    private val catalogSynchronizer: CurrencyCatalogSynchronizer,
    private val exchangeRateSynchronizer: CurrencyExchangeRateSynchronizer,
    @Value("\${pocketr.currency.base:EUR}")
    private val baseCurrency: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        refresh()
    }

    @Scheduled(
        fixedDelayString = "\${pocketr.currency.refresh-interval-ms:86400000}",
        initialDelayString = "\${pocketr.currency.refresh-interval-ms:86400000}",
    )
    fun refresh() {
        try {
            catalogSynchronizer.synchronize()
        } catch (ex: RuntimeException) {
            logger.warn("Currency catalog synchronization failed; keeping existing currencies.", ex)
        }

        try {
            exchangeRateSynchronizer.synchronize(baseCurrency)
        } catch (ex: RuntimeException) {
            logger.warn("Currency exchange-rate synchronization failed; keeping existing rates.", ex)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CurrencyDataRefreshScheduler::class.java)
    }
}
