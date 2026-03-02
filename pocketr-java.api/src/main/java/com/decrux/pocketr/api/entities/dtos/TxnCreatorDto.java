package com.decrux.pocketr.api.entities.dtos;

public record TxnCreatorDto(
    String firstName,
    String lastName,
    String email,
    String avatar
) {
    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatar() {
        return avatar;
    }
}
