package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.settings.AppSettings
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
@UsePostgresDb
@DisplayName("AppSettingsRepository")
class AppSettingsRepositoryTest
    @Autowired
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val testEntityManager: TestEntityManager,
    ) {
        @Test
        @DisplayName("stores the singleton app settings row")
        fun storesSingletonSettings() {
            val currency = persistCurrency("USD")

            appSettingsRepository.saveAndFlush(AppSettings(baseCurrency = currency))
            testEntityManager.clear()

            val settings = appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElseThrow()
            assertEquals("USD", settings.baseCurrency?.code)
        }

        @Test
        @DisplayName("rejects settings rows with a non-singleton id")
        fun rejectsNonSingletonId() {
            val currency = persistCurrency("USD")

            assertThrows(DataIntegrityViolationException::class.java) {
                appSettingsRepository.saveAndFlush(
                    AppSettings(
                        id = 2,
                        baseCurrency = currency,
                    ),
                )
            }
        }

        private fun persistCurrency(code: String): Currency {
            val currency =
                Currency(
                    code = code,
                    minorUnit = 2,
                    name = "Test Currency",
                )
            testEntityManager.persistAndFlush(currency)
            return currency
        }
    }
