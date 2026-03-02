package com.decrux.pocketr.api.entities.dtos;

public record UpdateCategoryDto(
    String name,
    String color
) {
    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
