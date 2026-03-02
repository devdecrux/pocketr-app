package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;
import java.util.UUID;

public record HouseholdAccountShareDto(
    UUID accountId,
    String accountName,
    String ownerEmail,
    String ownerFirstName,
    String ownerLastName,
    Instant sharedAt
) {
    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getOwnerFirstName() {
        return ownerFirstName;
    }

    public String getOwnerLastName() {
        return ownerLastName;
    }

    public Instant getSharedAt() {
        return sharedAt;
    }
}
