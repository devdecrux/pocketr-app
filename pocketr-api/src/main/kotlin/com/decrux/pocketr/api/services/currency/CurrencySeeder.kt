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
                Currency(code = "EUR", isoNumeric = "978", minorUnit = 2, name = "Euro", symbol = "EUR"),
                Currency(code = "USD", isoNumeric = "840", minorUnit = 2, name = "US Dollar", symbol = "$"),
                Currency(code = "GBP", isoNumeric = "826", minorUnit = 2, name = "British Pound", symbol = "GBP"),
                Currency(code = "JPY", isoNumeric = "392", minorUnit = 0, name = "Japanese Yen", symbol = "JPY"),
                Currency(code = "CHF", isoNumeric = "756", minorUnit = 2, name = "Swiss Franc", symbol = "CHF"),
                Currency(code = "BHD", isoNumeric = "048", minorUnit = 3, name = "Bahraini Dinar", symbol = "BHD"),
                Currency(code = "CAD", isoNumeric = "124", minorUnit = 2, name = "Canadian Dollar", symbol = "CA$"),
                Currency(code = "AUD", isoNumeric = "036", minorUnit = 2, name = "Australian Dollar", symbol = "A$"),
                Currency(code = "SEK", isoNumeric = "752", minorUnit = 2, name = "Swedish Krona", symbol = "SEK"),
                Currency(code = "NOK", isoNumeric = "578", minorUnit = 2, name = "Norwegian Krone", symbol = "NOK"),
                Currency(code = "DKK", isoNumeric = "208", minorUnit = 2, name = "Danish Krone", symbol = "DKK"),
                Currency(code = "PLN", isoNumeric = "985", minorUnit = 2, name = "Polish Zloty", symbol = "PLN"),
                Currency(code = "CZK", isoNumeric = "203", minorUnit = 2, name = "Czech Koruna", symbol = "CZK"),
                Currency(code = "HUF", isoNumeric = "348", minorUnit = 2, name = "Hungarian Forint", symbol = "HUF"),
                Currency(code = "RON", isoNumeric = "946", minorUnit = 2, name = "Romanian Leu", symbol = "RON"),
            )

        currencyRepository.saveAll(currencies)
    }
}
