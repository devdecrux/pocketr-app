package com.decrux.pocketr.api.entities.dtos;

import java.time.LocalDate;

public record BalanceTimeseriesPointDto(
    LocalDate date,
    long balanceMinor
) {
    public LocalDate getDate() {
        return date;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }
}
