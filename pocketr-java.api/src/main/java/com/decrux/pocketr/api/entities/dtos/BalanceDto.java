package com.decrux.pocketr.api.entities.dtos;

import java.time.LocalDate;
import java.util.UUID;

public record BalanceDto(
    UUID accountId,
    String accountName,
    String accountType,
    String currency,
    long balanceMinor,
    LocalDate asOf
) {
    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public LocalDate getAsOf() {
        return asOf;
    }
}
