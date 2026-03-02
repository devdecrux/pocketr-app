package com.decrux.pocketr.api.repositories.projections;

import java.time.LocalDate;

public record DailyNetProjection(
    LocalDate txnDate,
    long netMinor
) {
    public LocalDate getTxnDate() {
        return txnDate;
    }

    public long getNetMinor() {
        return netMinor;
    }
}
