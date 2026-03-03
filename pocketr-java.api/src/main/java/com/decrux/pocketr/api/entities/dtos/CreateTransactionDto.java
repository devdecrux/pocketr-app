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
    public CreateTransactionDto {
        RequestDtoValidator.requireNotNull(txnDate, "txnDate");
        RequestDtoValidator.requireCurrencyCode(currency, "currency");
        RequestDtoValidator.requireNotBlank(description, "description");
        RequestDtoValidator.requireMaxLength(description, 255, "description");
        RequestDtoValidator.requireMinSize(splits, 2, "splits");

        for (CreateSplitDto split : splits) {
            RequestDtoValidator.requireNotNull(split, "splits item");
        }
    }

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
