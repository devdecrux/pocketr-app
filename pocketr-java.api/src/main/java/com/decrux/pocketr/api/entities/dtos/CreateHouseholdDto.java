package com.decrux.pocketr.api.entities.dtos;

public record CreateHouseholdDto(
    String name
) {
    public String getName() {
        return name;
    }
}
