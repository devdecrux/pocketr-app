package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.repositories.CurrencyRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CurrencySeeder(
    private val currencyRepository: CurrencyRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (currencyRepository.count() > 0) return

        val currencies =
            listOf(
                Currency(code = "EUR", minorUnit = 2, name = "Euro"),
                Currency(code = "USD", minorUnit = 2, name = "US Dollar"),
                Currency(code = "GBP", minorUnit = 2, name = "British Pound"),
                Currency(code = "JPY", minorUnit = 0, name = "Japanese Yen"),
                Currency(code = "CHF", minorUnit = 2, name = "Swiss Franc"),
                Currency(code = "BHD", minorUnit = 3, name = "Bahraini Dinar"),
                Currency(code = "CAD", minorUnit = 2, name = "Canadian Dollar"),
                Currency(code = "AUD", minorUnit = 2, name = "Australian Dollar"),
                Currency(code = "SEK", minorUnit = 2, name = "Swedish Krona"),
                Currency(code = "NOK", minorUnit = 2, name = "Norwegian Krone"),
                Currency(code = "DKK", minorUnit = 2, name = "Danish Krone"),
                Currency(code = "PLN", minorUnit = 2, name = "Polish Zloty"),
                Currency(code = "CZK", minorUnit = 2, name = "Czech Koruna"),
                Currency(code = "HUF", minorUnit = 2, name = "Hungarian Forint"),
                Currency(code = "RON", minorUnit = 2, name = "Romanian Leu"),
            )

        currencyRepository.saveAll(currencies)
    }
}
