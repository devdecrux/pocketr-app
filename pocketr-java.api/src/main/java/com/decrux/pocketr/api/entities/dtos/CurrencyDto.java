package com.decrux.pocketr.api.entities.dtos;

public record CurrencyDto(
    String code,
    short minorUnit,
    String name
) {
    public String getCode() {
        return code;
    }

    public short getMinorUnit() {
        return minorUnit;
    }

    public String getName() {
        return name;
    }
}
