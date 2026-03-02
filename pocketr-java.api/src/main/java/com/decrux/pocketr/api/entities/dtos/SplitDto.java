package com.decrux.pocketr.api.entities.dtos;

import java.util.UUID;

public record SplitDto(
    UUID id,
    UUID accountId,
    String accountName,
    String accountType,
    String side,
    long amountMinor,
    long effectMinor,
    UUID categoryTagId,
    String categoryTagName
) {
    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getSide() {
        return side;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getEffectMinor() {
        return effectMinor;
    }

    public UUID getCategoryTagId() {
        return categoryTagId;
    }

    public String getCategoryTagName() {
        return categoryTagName;
    }
}
