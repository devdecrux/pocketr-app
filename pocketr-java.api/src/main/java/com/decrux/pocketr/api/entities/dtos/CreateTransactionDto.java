package com.decrux.pocketr.api.entities.dtos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateTransactionDto(
    String mode,
    UUID householdId,
    LocalDate txnDate,
    String currency,
    String description,
    List<CreateSplitDto> splits
) {
    public String getMode() {
        return mode;
    }

    public UUID getHouseholdId() {
        return householdId;
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

    public List<CreateSplitDto> getSplits() {
        return splits;
    }
}
