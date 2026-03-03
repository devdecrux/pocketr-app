package com.decrux.pocketr.api.entities.dtos;

public record InviteMemberDto(
    String email
) {
    public InviteMemberDto {
        RequestDtoValidator.requireEmail(email, "email");
        RequestDtoValidator.requireMaxLength(email, 255, "email");
    }

    public String getEmail() {
        return email;
    }
}
