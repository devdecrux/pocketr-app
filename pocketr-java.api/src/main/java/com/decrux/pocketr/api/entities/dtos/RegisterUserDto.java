package com.decrux.pocketr.api.entities.dtos;

public record RegisterUserDto(
    String password,
    String email,
    String firstName,
    String lastName
) {
    public RegisterUserDto {
        RequestDtoValidator.requireNotBlank(password, "password");
        RequestDtoValidator.requireEmail(email, "email");
        RequestDtoValidator.requireMaxLength(email, 255, "email");
    }

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
