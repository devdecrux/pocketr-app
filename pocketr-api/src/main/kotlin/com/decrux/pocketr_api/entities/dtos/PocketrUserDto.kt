package com.decrux.pocketr_api.entities.dtos

data class PocketrUserDto(
    val id: Long?,
    val email: String,
    val username: String,
    val firstName: String?,
    val lastName: String?,
)
