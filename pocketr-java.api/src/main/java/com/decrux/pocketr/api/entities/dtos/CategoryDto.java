package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;
import java.util.UUID;

public record CategoryDto(
    UUID id,
    String name,
    String color,
    Instant createdAt
) {
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
