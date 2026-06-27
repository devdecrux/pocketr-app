package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.repositories.CurrencyExchangeRateRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.dtos.Rate
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@DisplayName("CurrencyExchangeRateSynchronizer")
class CurrencyExchangeRateSynchronizerTest {
    private lateinit var frankfurterClient: FrankfurterClient
    private lateinit var currencyRepository: CurrencyRepository
    private lateinit var exchangeRateRepository: CurrencyExchangeRateRepository
    private lateinit var synchronizer: CurrencyExchangeRateSynchronizer
    private lateinit var savedRates: List<CurrencyExchangeRate>

    private val eur = Currency(code = "EUR", minorUnit = 2, name = "Euro", symbol = "€")
    private val usd = Currency(code = "USD", minorUnit = 2, name = "US Dollar", symbol = "$")
    private val bgn = Currency(code = "BGN", minorUnit = 2, name = "Bulgarian Lev", symbol = "лв")
    private val now = Instant.parse("2026-02-20T00:00:00Z")

    @BeforeEach
    fun setUp() {
        frankfurterClient = mock(FrankfurterClient::class.java)
        currencyRepository = mock(CurrencyRepository::class.java)
        exchangeRateRepository = mock(CurrencyExchangeRateRepository::class.java)
        savedRates = emptyList()
        doAnswer { invocation ->
            savedRates = (invocation.arguments[0] as Iterable<*>).map { it as CurrencyExchangeRate }
            savedRates
        }.`when`(exchangeRateRepository).saveAll(anyRates())
        synchronizer =
            CurrencyExchangeRateSynchronizer(
                frankfurterClient,
                currencyRepository,
                exchangeRateRepository,
                Clock.fixed(now, ZoneOffset.UTC),
            )
    }

    @Test
    fun replacesCurrentRowsForBaseAfterValidatingFullSet() {
        val rates =
            listOf(
                Rate(LocalDate.of(2026, 2, 20), "EUR", "USD", BigDecimal("1.08")),
                Rate(LocalDate.of(2026, 2, 20), "EUR", "BGN", BigDecimal("1.95583")),
            )
        `when`(frankfurterClient.fetchRates("EUR")).thenReturn(rates)
        `when`(currencyRepository.findById("EUR")).thenReturn(Optional.of(eur))
        `when`(currencyRepository.findAllById(listOf("USD", "BGN"))).thenReturn(listOf(usd, bgn))

        synchronizer.update("eur")

        val inOrder = inOrder(currencyRepository, exchangeRateRepository)
        inOrder.verify(currencyRepository).findAllById(listOf("USD", "BGN"))
        inOrder.verify(exchangeRateRepository).deleteByIdBaseCurrency("EUR")
        inOrder.verify(exchangeRateRepository).saveAll(anyRates())

        val saved = savedRates.associateBy { it.id.quoteCurrency }
        assertEquals(BigDecimal("1.08"), saved.getValue("USD").rate)
        assertEquals(BigDecimal("1.95583"), saved.getValue("BGN").rate)
        assertEquals(now, saved.getValue("USD").updatedAt)
    }

    @Test
    fun emptyProviderResponsePreservesExistingRows() {
        `when`(frankfurterClient.fetchRates("EUR")).thenReturn(emptyList())

        synchronizer.update("EUR")

        verify(exchangeRateRepository, never()).deleteByIdBaseCurrency("EUR")
        verify(exchangeRateRepository, never()).saveAll(anyRates())
    }

    @Test
    fun failedProviderResponsePreservesExistingRows() {
        `when`(frankfurterClient.fetchRates("EUR")).thenThrow(IllegalStateException("provider down"))

        assertThrows(IllegalStateException::class.java) {
            synchronizer.update("EUR")
        }

        verifyNoMoreInteractions(exchangeRateRepository)
    }

    @Test
    fun missingQuoteCurrencyDoesNotPartiallyReplaceRows() {
        `when`(frankfurterClient.fetchRates("EUR"))
            .thenReturn(listOf(Rate(LocalDate.of(2026, 2, 20), "EUR", "USD", BigDecimal("1.08"))))
        `when`(currencyRepository.findById("EUR")).thenReturn(Optional.of(eur))
        `when`(currencyRepository.findAllById(listOf("USD"))).thenReturn(emptyList())

        val ex =
            assertThrows(IllegalStateException::class.java) {
                synchronizer.update("EUR")
            }

        assertEquals("Quote currencies are missing: [USD]", ex.message)
        verify(exchangeRateRepository, never()).deleteByIdBaseCurrency("EUR")
        verify(exchangeRateRepository, never()).saveAll(anyRates())
    }

    @Test
    fun mismatchedProviderBaseDoesNotPartiallyReplaceRows() {
        `when`(frankfurterClient.fetchRates("EUR"))
            .thenReturn(listOf(Rate(LocalDate.of(2026, 2, 20), "USD", "BGN", BigDecimal("1.80"))))

        val ex =
            assertThrows(IllegalStateException::class.java) {
                synchronizer.update("EUR")
            }

        assertEquals("Frankfurter returned rates for unexpected base currencies: [USD]", ex.message)
        verify(exchangeRateRepository, never()).deleteByIdBaseCurrency("EUR")
        verify(exchangeRateRepository, never()).saveAll(anyRates())
    }

    private fun anyRates(): Iterable<CurrencyExchangeRate> {
        ArgumentMatchers.any(Iterable::class.java)
        return emptyList()
    }
}
