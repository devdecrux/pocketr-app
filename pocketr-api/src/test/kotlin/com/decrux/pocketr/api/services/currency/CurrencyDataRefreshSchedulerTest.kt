package com.decrux.pocketr.api.services.currency

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@DisplayName("CurrencyDataRefreshScheduler")
class CurrencyDataRefreshSchedulerTest {
    private lateinit var catalogSynchronizer: CurrencyCatalogSynchronizer
    private lateinit var exchangeRateSynchronizer: CurrencyExchangeRateSynchronizer
    private lateinit var scheduler: CurrencyDataRefreshScheduler

    @BeforeEach
    fun setUp() {
        catalogSynchronizer = mock(CurrencyCatalogSynchronizer::class.java)
        exchangeRateSynchronizer = mock(CurrencyExchangeRateSynchronizer::class.java)
        scheduler = CurrencyDataRefreshScheduler(catalogSynchronizer, exchangeRateSynchronizer, "BGN")
    }

    @Test
    fun startupDelegatesToBothSynchronizersWithConfiguredBase() {
        scheduler.run(mock(org.springframework.boot.ApplicationArguments::class.java))

        verify(catalogSynchronizer).synchronize()
        verify(exchangeRateSynchronizer).synchronize("BGN")
    }

    @Test
    fun scheduledRefreshDelegatesToBothSynchronizersWithConfiguredBase() {
        scheduler.refresh()

        verify(catalogSynchronizer).synchronize()
        verify(exchangeRateSynchronizer).synchronize("BGN")
    }

    @Test
    fun catalogFailureDoesNotPreventRateRefresh() {
        `when`(catalogSynchronizer.synchronize()).thenThrow(IllegalStateException("catalog failed"))

        assertDoesNotThrow { scheduler.refresh() }

        verify(exchangeRateSynchronizer).synchronize("BGN")
    }

    @Test
    fun rateFailureDoesNotPreventCatalogRefreshOrEscape() {
        `when`(exchangeRateSynchronizer.synchronize("BGN")).thenThrow(IllegalStateException("rates failed"))

        assertDoesNotThrow { scheduler.refresh() }

        verify(catalogSynchronizer).synchronize()
        verify(exchangeRateSynchronizer).synchronize("BGN")
    }
}
