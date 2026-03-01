package com.decrux.pocketr.api.entities.dtos

data class UserDto(
    val id: Long?,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val avatar: String?,
)
