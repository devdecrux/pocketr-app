package com.decrux.pocketr.api.entities.dtos;

import java.util.UUID;

public record CreateSplitDto(
    UUID accountId,
    String side,
    long amountMinor,
    UUID categoryTagId
) {
    public CreateSplitDto {
        RequestDtoValidator.requireNotNull(accountId, "accountId");
        RequestDtoValidator.requireNotBlank(side, "side");
        RequestDtoValidator.requirePositive(amountMinor, "amountMinor");
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSide() {
        return side;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public UUID getCategoryTagId() {
        return categoryTagId;
    }
}
