package com.decrux.pocketr_api.entities.dtos

data class RegisterUserDto(
    val username: String? = null,
    val password: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
)
