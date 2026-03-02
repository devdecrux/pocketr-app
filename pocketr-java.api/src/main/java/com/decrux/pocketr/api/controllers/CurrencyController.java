package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.CurrencyDto;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/currencies")
public class CurrencyController {

    private final CurrencyRepository currencyRepository;

    public CurrencyController(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @GetMapping
    public List<CurrencyDto> listCurrencies() {
        return currencyRepository.findAll().stream().map(this::toDto).toList();
    }

    private CurrencyDto toDto(Currency currency) {
        return new CurrencyDto(currency.getCode(), currency.getMinorUnit(), currency.getName());
    }
}
