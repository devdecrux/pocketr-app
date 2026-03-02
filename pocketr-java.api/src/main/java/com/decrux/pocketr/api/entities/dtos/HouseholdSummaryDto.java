package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;
import java.util.UUID;

public record HouseholdSummaryDto(
    UUID id,
    String name,
    String role,
    String status,
    Instant createdAt
) {
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
