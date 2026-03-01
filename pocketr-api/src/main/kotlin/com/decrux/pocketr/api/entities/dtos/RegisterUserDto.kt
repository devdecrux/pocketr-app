package com.decrux.pocketr.api.entities.dtos

data class RegisterUserDto(
    val password: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
)
