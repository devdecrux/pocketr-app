package com.decrux.pocketr.api.entities.dtos;

public record UserDto(
    Long id,
    String email,
    String firstName,
    String lastName,
    String avatar
) {
    public Long getId() {
        return id;
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

    public String getAvatar() {
        return avatar;
    }
}
