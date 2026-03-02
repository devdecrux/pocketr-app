package com.decrux.pocketr.api.entities.dtos;

public record UpdateAccountDto(
    String name
) {
    public String getName() {
        return name;
    }
}
