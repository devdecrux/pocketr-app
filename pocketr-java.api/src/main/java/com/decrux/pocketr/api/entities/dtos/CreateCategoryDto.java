package com.decrux.pocketr.api.entities.dtos;

public record CreateCategoryDto(
    String name,
    String color
) {
    public CreateCategoryDto {
        RequestDtoValidator.requireNotBlank(name, "name");
        RequestDtoValidator.requireMaxLength(name, 255, "name");
        RequestDtoValidator.requireMaxLength(color, 7, "color");
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
