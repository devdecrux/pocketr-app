package com.decrux.pocketr.api.repositories.projections;

import java.util.UUID;

public record MonthlyExpenseProjection(
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
