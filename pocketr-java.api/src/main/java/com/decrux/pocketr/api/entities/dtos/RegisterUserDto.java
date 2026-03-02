package com.decrux.pocketr.api.entities.dtos;

public record RegisterUserDto(
    String password,
    String email,
    String firstName,
    String lastName
) {
    public String getPassword() {
        return password;
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
}
