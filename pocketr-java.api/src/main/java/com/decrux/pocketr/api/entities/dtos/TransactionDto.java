package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransactionDto(
    UUID id,
    LocalDate txnDate,
    String currency,
    String description,
    UUID householdId,
    String txnKind,
    TxnCreatorDto createdBy,
    List<SplitDto> splits,
    Instant createdAt,
    Instant updatedAt
) {
    public UUID getId() {
        return id;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getTxnKind() {
        return txnKind;
    }

    public TxnCreatorDto getCreatedBy() {
        return createdBy;
    }

    public List<SplitDto> getSplits() {
        return splits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
