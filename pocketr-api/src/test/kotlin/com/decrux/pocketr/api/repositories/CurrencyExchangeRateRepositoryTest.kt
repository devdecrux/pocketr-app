package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@UsePostgresDb
@DisplayName("CurrencyExchangeRateRepository")
class CurrencyExchangeRateRepositoryTest
    @Autowired
    constructor(
        private val currencyRepository: CurrencyRepository,
        private val exchangeRateRepository: CurrencyExchangeRateRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        private lateinit var eur: Currency
        private lateinit var usd: Currency

        @BeforeEach
        fun setUp() {
            exchangeRateRepository.deleteAll()
            currencyRepository.deleteAll()
            eur = currencyRepository.save(Currency(code = "EUR", isoNumeric = "978", minorUnit = 2, name = "Euro", symbol = "€"))
            usd = currencyRepository.save(Currency(code = "USD", isoNumeric = "840", minorUnit = 2, name = "US Dollar", symbol = "$"))
        }

        @Test
        fun persistsCompositePrimaryKeyAndFindsByBase() {
            exchangeRateRepository.saveAndFlush(rate("EUR", "USD", BigDecimal("1.08")))

            val result = exchangeRateRepository.findAllByIdBaseCurrency("EUR")

            assertEquals(1, result.size)
            assertEquals(CurrencyExchangeRateId("EUR", "USD"), result.single().id)
            assertEquals(BigDecimal("1.080000000000000000"), result.single().rate)
        }

        @Test
        fun compositePrimaryKeyRejectsDuplicateBaseQuoteRows() {
            insertRate("EUR", "USD", BigDecimal("1.08"))

            assertThrows(DataIntegrityViolationException::class.java) {
                insertRate("EUR", "USD", BigDecimal("1.09"))
            }
        }

        @Test
        fun foreignKeysRequireExistingBaseAndQuoteCurrencies() {
            assertThrows(DataIntegrityViolationException::class.java) {
                insertRate("EUR", "BGN", BigDecimal("1.95583"))
            }
        }

        @Test
        fun positiveRateConstraintRejectsZeroRate() {
            assertThrows(DataIntegrityViolationException::class.java) {
                exchangeRateRepository.saveAndFlush(rate("EUR", "USD", BigDecimal.ZERO))
            }
        }

        private fun rate(
            baseCode: String,
            quoteCode: String,
            value: BigDecimal,
        ): CurrencyExchangeRate =
            CurrencyExchangeRate(
                id = CurrencyExchangeRateId(baseCode, quoteCode),
                base = if (baseCode == "EUR") eur else usd,
                quote = if (quoteCode == "EUR") eur else usd,
                rate = value,
                providerDate = LocalDate.of(2026, 2, 20),
                updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
            )

        private fun insertRate(
            baseCode: String,
            quoteCode: String,
            value: BigDecimal,
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO currencies_exchange_rates (base_currency, quote_currency, rate, provider_date, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                baseCode,
                quoteCode,
                value,
                LocalDate.of(2026, 2, 20),
                Timestamp.from(Instant.parse("2026-02-20T00:00:00Z")),
            )
        }
    }
