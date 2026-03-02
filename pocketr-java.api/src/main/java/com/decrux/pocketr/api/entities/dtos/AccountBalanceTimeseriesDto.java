package com.decrux.pocketr.api.entities.dtos;

import java.util.List;
import java.util.UUID;

public record AccountBalanceTimeseriesDto(
    UUID accountId,
    String accountName,
    String accountType,
    String currency,
    List<BalanceTimeseriesPointDto> points
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

    public List<BalanceTimeseriesPointDto> getPoints() {
        return points;
    }
}
