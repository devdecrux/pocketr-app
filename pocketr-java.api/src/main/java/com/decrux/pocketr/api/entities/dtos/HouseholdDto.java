package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HouseholdDto(
    UUID id,
    String name,
    Instant createdAt,
    List<HouseholdMemberDto> members
) {
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<HouseholdMemberDto> getMembers() {
        return members;
    }
}
