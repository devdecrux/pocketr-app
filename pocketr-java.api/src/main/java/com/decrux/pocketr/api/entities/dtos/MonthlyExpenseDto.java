package com.decrux.pocketr.api.entities.dtos;

import java.util.UUID;

public record MonthlyExpenseDto(
    UUID expenseAccountId,
    String expenseAccountName,
    UUID categoryTagId,
    String categoryTagName,
    String currency,
    long netMinor
) {
    public UUID getExpenseAccountId() {
        return expenseAccountId;
    }

    public String getExpenseAccountName() {
        return expenseAccountName;
    }

    public UUID getCategoryTagId() {
        return categoryTagId;
    }

    public String getCategoryTagName() {
        return categoryTagName;
    }

    public String getCurrency() {
        return currency;
    }

    public long getNetMinor() {
        return netMinor;
    }
}
