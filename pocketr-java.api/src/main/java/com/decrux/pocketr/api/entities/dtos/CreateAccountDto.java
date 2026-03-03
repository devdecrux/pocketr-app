package com.decrux.pocketr.api.entities.dtos;

import java.time.LocalDate;

public record CreateAccountDto(
    String name,
    String type,
    String currency,
    Long openingBalanceMinor,
    LocalDate openingBalanceDate
) {
    public CreateAccountDto {
        RequestDtoValidator.requireNotBlank(name, "name");
        RequestDtoValidator.requireMaxLength(name, 255, "name");
        RequestDtoValidator.requireNotBlank(type, "type");
        RequestDtoValidator.requireCurrencyCode(currency, "currency");
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getOpeningBalanceMinor() {
        return openingBalanceMinor;
    }

    public LocalDate getOpeningBalanceDate() {
        return openingBalanceDate;
    }
}
