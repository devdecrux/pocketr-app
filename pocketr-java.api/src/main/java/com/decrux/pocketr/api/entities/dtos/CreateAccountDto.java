package com.decrux.pocketr.api.entities.dtos;

import java.time.LocalDate;

public record CreateAccountDto(
    String name,
    String type,
    String currency,
    Long openingBalanceMinor,
    LocalDate openingBalanceDate
) {
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
