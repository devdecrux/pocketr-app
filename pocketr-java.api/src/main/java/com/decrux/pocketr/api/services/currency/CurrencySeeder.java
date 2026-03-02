package com.decrux.pocketr.api.services.currency;

import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CurrencySeeder implements ApplicationRunner {

    private final CurrencyRepository currencyRepository;

    public CurrencySeeder(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (currencyRepository.count() > 0) {
            return;
        }

        List<Currency> currencies = List.of(
            currency("EUR", (short) 2, "Euro"),
            currency("USD", (short) 2, "US Dollar"),
            currency("GBP", (short) 2, "British Pound"),
            currency("JPY", (short) 0, "Japanese Yen"),
            currency("CHF", (short) 2, "Swiss Franc"),
            currency("BHD", (short) 3, "Bahraini Dinar"),
            currency("CAD", (short) 2, "Canadian Dollar"),
            currency("AUD", (short) 2, "Australian Dollar"),
            currency("SEK", (short) 2, "Swedish Krona"),
            currency("NOK", (short) 2, "Norwegian Krone"),
            currency("DKK", (short) 2, "Danish Krone"),
            currency("PLN", (short) 2, "Polish Zloty"),
            currency("CZK", (short) 2, "Czech Koruna"),
            currency("HUF", (short) 2, "Hungarian Forint"),
            currency("RON", (short) 2, "Romanian Leu")
        );

        currencyRepository.saveAll(currencies);
    }

    private static Currency currency(String code, short minorUnit, String name) {
        Currency currency = new Currency();
        currency.setCode(code);
        currency.setMinorUnit(minorUnit);
        currency.setName(name);
        return currency;
    }
}
