package com.decrux.pocketr.api.entities.dtos;

public record InviteMemberDto(
    String email
) {
    public String getEmail() {
        return email;
    }
}
