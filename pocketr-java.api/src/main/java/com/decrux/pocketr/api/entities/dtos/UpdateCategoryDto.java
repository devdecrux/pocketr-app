package com.decrux.pocketr.api.entities.dtos;

public record UpdateCategoryDto(
    String name,
    String color
) {
    public UpdateCategoryDto {
        RequestDtoValidator.requireLengthInRange(name, 1, 255, "name");
        RequestDtoValidator.requireMaxLength(color, 7, "color");
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
