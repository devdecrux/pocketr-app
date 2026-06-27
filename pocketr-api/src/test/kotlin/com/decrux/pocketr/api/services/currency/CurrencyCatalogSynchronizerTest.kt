package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.services.currency.frankfurter.FrankfurterClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import com.decrux.pocketr.api.services.currency.dtos.Currency as ProviderCurrencyDto

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
        val existingEur = Currency(code = "EUR", minorUnit = 9, name = "Old", symbol = "Old")
        `when`(frankfurterClient.fetchCurrencies())
            .thenReturn(
                listOf(
                    ProviderCurrencyDto(code = "EUR", name = "Euro", symbol = "€"),
                    ProviderCurrencyDto(code = "JPY", name = "Yen", symbol = "¥"),
                    ProviderCurrencyDto(code = "XXX", name = "Unknown", symbol = "X"),
                ),
            )
        `when`(currencyRepository.findAllById(listOf("EUR", "JPY", "XXX"))).thenReturn(listOf(existingEur))

        synchronizer.update()

        val saved = savedCurrencies.associateBy { it.code }
        assertEquals("Euro", existingEur.name)
        assertEquals("€", existingEur.symbol)
        assertEquals(2, saved.getValue("EUR").minorUnit.toInt())
        assertEquals(0, saved.getValue("JPY").minorUnit.toInt())
        assertEquals(2, saved.getValue("XXX").minorUnit.toInt())
    }

    @Test
    fun emptyProviderResponseKeepsExistingCatalogUnchanged() {
        `when`(frankfurterClient.fetchCurrencies()).thenReturn(emptyList())

        synchronizer.update()

        verify(currencyRepository, never()).saveAll(anyCurrencyIterable())
    }

    @Test
    fun doesNotDeleteCurrenciesMissingFromLaterProviderResponses() {
        `when`(frankfurterClient.fetchCurrencies())
            .thenReturn(listOf(ProviderCurrencyDto(code = "EUR", name = "Euro", symbol = "€")))
        `when`(currencyRepository.findAllById(listOf("EUR"))).thenReturn(emptyList())

        synchronizer.update()

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
