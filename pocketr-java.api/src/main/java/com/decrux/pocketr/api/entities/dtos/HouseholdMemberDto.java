package com.decrux.pocketr.api.entities.dtos;

import java.time.Instant;

public record HouseholdMemberDto(
    long userId,
    String email,
    String firstName,
    String lastName,
    String role,
    String status,
    Instant joinedAt
) {
    public long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
