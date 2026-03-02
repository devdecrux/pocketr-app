package com.decrux.pocketr.api.entities.dtos;

import java.util.UUID;

public record ShareAccountDto(
    UUID accountId
) {
    public UUID getAccountId() {
        return accountId;
    }
}
