package com.decrux.pocketr.api.services.currency.frankfurter

import com.decrux.pocketr.api.services.currency.dtos.Currency
import com.decrux.pocketr.api.services.currency.dtos.Rate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("FrankfurterClient")
class FrankfurterClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: FrankfurterClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = FrankfurterClient(builder, "https://provider.test/")
    }

    @Test
    fun mapsCurrencyResponseAndUsesConfiguredBaseUrl() {
        server
            .expect(requestTo("https://provider.test/v2/currencies"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    [
                      {"iso_code":"eur","name":"Euro","symbol":"€"},
                      {"iso_code":"usd","name":"US Dollar","symbol":"$"}
                    ]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.fetchCurrencies()

        assertEquals(
            listOf(
                Currency(code = "EUR", name = "Euro", symbol = "€"),
                Currency(code = "USD", name = "US Dollar", symbol = "$"),
            ),
            result,
        )
        server.verify()
    }

    @Test
    fun mapsRateResponseAndUppercasesRequestedBase() {
        server
            .expect(requestTo("https://provider.test/v2/rates?base=EUR"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    [
                      {"date":"2026-02-20","base":"eur","quote":"usd","rate":1.08}
                    ]
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.fetchRates("eur")

        assertEquals(
            listOf(
                Rate(
                    date = LocalDate.of(2026, 2, 20),
                    base = "EUR",
                    quote = "USD",
                    rate = BigDecimal("1.08"),
                ),
            ),
            result,
        )
        server.verify()
    }

    @Test
    fun missingRequiredCurrencyFieldFailsClearly() {
        server
            .expect(requestTo("https://provider.test/v2/currencies"))
            .andRespond(
                withSuccess(
                    """[{"name":"Euro","symbol":"€"}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                client.fetchCurrencies()
            }

        assertEquals("Missing Frankfurter field: iso_code", ex.message)
    }

    @Test
    fun missingCurrencySymbolFailsClearly() {
        server
            .expect(requestTo("https://provider.test/v2/currencies"))
            .andRespond(
                withSuccess(
                    """[{"iso_code":"EUR","name":"Euro"}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                client.fetchCurrencies()
            }

        assertEquals("Missing Frankfurter field: symbol", ex.message)
    }

    @Test
    fun invalidRateFieldFailsClearly() {
        server
            .expect(requestTo("https://provider.test/v2/rates?base=EUR"))
            .andRespond(
                withSuccess(
                    """[{"date":"2026-02-20","base":"EUR","quote":"USD","rate":0}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                client.fetchRates("EUR")
            }

        assertEquals("Invalid Frankfurter field: rate", ex.message)
    }
}
