package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterCurrency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@DisplayName("CurrencyCatalogSynchronizer")
class CurrencyCatalogSynchronizerTest {
    private lateinit var frankfurterClient: FrankfurterClient
    private lateinit var currencyRepository: CurrencyRepository
    private lateinit var synchronizer: CurrencyCatalogSynchronizer
    private lateinit var savedCurrencies: List<Currency>

    @BeforeEach
    fun setUp() {
        frankfurterClient = mock(FrankfurterClient::class.java)
        currencyRepository = mock(CurrencyRepository::class.java)
        savedCurrencies = emptyList()
        doAnswer { invocation ->
            savedCurrencies = (invocation.arguments[0] as Iterable<*>).map { it as Currency }
            savedCurrencies
        }.`when`(currencyRepository).saveAll(anyCurrencyIterable())
        synchronizer = CurrencyCatalogSynchronizer(frankfurterClient, currencyRepository)
    }

    @Test
    fun upsertsCurrenciesAndDerivesMinorUnits() {
        val existingEur = Currency(code = "EUR", isoNumeric = "old", minorUnit = 9, name = "Old", symbol = "Old")
        `when`(frankfurterClient.fetchCurrencies())
            .thenReturn(
                listOf(
                    FrankfurterCurrency(code = "EUR", isoNumeric = "978", name = "Euro", symbol = "€"),
                    FrankfurterCurrency(code = "JPY", isoNumeric = "392", name = "Yen", symbol = "¥"),
                    FrankfurterCurrency(code = "XXX", isoNumeric = "999", name = "Unknown", symbol = "X"),
                ),
            )
        `when`(currencyRepository.findAllById(listOf("EUR", "JPY", "XXX"))).thenReturn(listOf(existingEur))

        synchronizer.synchronize()

        val saved = savedCurrencies.associateBy { it.code }
        assertEquals("978", existingEur.isoNumeric)
        assertEquals("Euro", existingEur.name)
        assertEquals("€", existingEur.symbol)
        assertEquals(2, saved.getValue("EUR").minorUnit.toInt())
        assertEquals(0, saved.getValue("JPY").minorUnit.toInt())
        assertEquals(2, saved.getValue("XXX").minorUnit.toInt())
    }

    @Test
    fun emptyProviderResponseKeepsExistingCatalogUnchanged() {
        `when`(frankfurterClient.fetchCurrencies()).thenReturn(emptyList())

        synchronizer.synchronize()

        verify(currencyRepository, never()).saveAll(anyCurrencyIterable())
    }

    @Test
    fun doesNotDeleteCurrenciesMissingFromLaterProviderResponses() {
        `when`(frankfurterClient.fetchCurrencies())
            .thenReturn(listOf(FrankfurterCurrency(code = "EUR", isoNumeric = "978", name = "Euro", symbol = "€")))
        `when`(currencyRepository.findAllById(listOf("EUR"))).thenReturn(emptyList())

        synchronizer.synchronize()

        verify(currencyRepository, never()).delete(anyCurrency())
        assertEquals(listOf("EUR"), savedCurrencies.map { it.code })
    }

    private fun anyCurrencyIterable(): Iterable<Currency> {
        ArgumentMatchers.any(Iterable::class.java)
        return emptyList()
    }

    private fun anyCurrency(): Currency {
        ArgumentMatchers.any(Currency::class.java)
        return Currency()
    }
}
