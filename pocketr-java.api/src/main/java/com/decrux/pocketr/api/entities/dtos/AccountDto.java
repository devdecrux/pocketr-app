package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;
import java.util.UUID;

public record AccountDto(
    UUID id,
    long ownerUserId,
    String name,
    String type,
    String currency,
    Instant createdAt
) {
    public UUID getId() {
        return id;
    }

    public long getOwnerUserId() {
        return ownerUserId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
