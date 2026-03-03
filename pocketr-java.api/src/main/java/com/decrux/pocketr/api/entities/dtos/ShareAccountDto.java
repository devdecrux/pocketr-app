package com.decrux.pocketr.api.entities.dtos;

import java.util.UUID;

public record ShareAccountDto(
    UUID accountId
) {
    public ShareAccountDto {
        RequestDtoValidator.requireNotNull(accountId, "accountId");
    }

    public UUID getAccountId() {
        return accountId;
    }
}
