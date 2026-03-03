package com.decrux.pocketr.api.entities.dtos;

public record UpdateAccountDto(
    String name
) {
    public UpdateAccountDto {
        RequestDtoValidator.requireLengthInRange(name, 1, 255, "name");
    }

    public String getName() {
        return name;
    }
}
