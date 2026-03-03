package com.decrux.pocketr.api.entities.dtos;

public record CreateHouseholdDto(
    String name
) {
    public CreateHouseholdDto {
        RequestDtoValidator.requireNotBlank(name, "name");
        RequestDtoValidator.requireMaxLength(name, 255, "name");
    }

    public String getName() {
        return name;
    }
}
