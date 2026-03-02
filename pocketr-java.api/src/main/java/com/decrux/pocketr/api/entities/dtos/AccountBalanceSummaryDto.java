package com.decrux.pocketr.api.entities.dtos;

import java.util.UUID;

public record AccountBalanceSummaryDto(
    UUID accountId,
    String accountName,
    String accountType,
    String currency,
    long balanceMinor
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
}
