package com.decrux.pocketr.api.repositories.projections;

import java.util.UUID;

public record AccountRawBalanceProjection(
    UUID accountId,
    long rawBalance
) {
    public UUID getAccountId() {
        return accountId;
    }

    public long getRawBalance() {
        return rawBalance;
    }
}
