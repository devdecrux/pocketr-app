package com.decrux.pocketr.api.services.currency

import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.repositories.CurrencyRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(0)
class CurrencySeeder(
    private val currencyRepository: CurrencyRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (currencyRepository.count() > 0) return

        val currencies =
            listOf(
                Currency(code = "EUR", minorUnit = 2, name = "Euro", symbol = "EUR"),
                Currency(code = "USD", minorUnit = 2, name = "US Dollar", symbol = "$"),
                Currency(code = "GBP", minorUnit = 2, name = "British Pound", symbol = "GBP"),
                Currency(code = "JPY", minorUnit = 0, name = "Japanese Yen", symbol = "JPY"),
                Currency(code = "CHF", minorUnit = 2, name = "Swiss Franc", symbol = "CHF"),
                Currency(code = "BHD", minorUnit = 3, name = "Bahraini Dinar", symbol = "BHD"),
                Currency(code = "CAD", minorUnit = 2, name = "Canadian Dollar", symbol = "CA$"),
                Currency(code = "AUD", minorUnit = 2, name = "Australian Dollar", symbol = "A$"),
                Currency(code = "SEK", minorUnit = 2, name = "Swedish Krona", symbol = "SEK"),
                Currency(code = "NOK", minorUnit = 2, name = "Norwegian Krone", symbol = "NOK"),
                Currency(code = "DKK", minorUnit = 2, name = "Danish Krone", symbol = "DKK"),
                Currency(code = "PLN", minorUnit = 2, name = "Polish Zloty", symbol = "PLN"),
                Currency(code = "CZK", minorUnit = 2, name = "Czech Koruna", symbol = "CZK"),
                Currency(code = "HUF", minorUnit = 2, name = "Hungarian Forint", symbol = "HUF"),
                Currency(code = "RON", minorUnit = 2, name = "Romanian Leu", symbol = "RON"),
            )

        currencyRepository.saveAll(currencies)
    }
}
