package com.decrux.pocketr.api.entities.dtos

data class UpdateCategoryDto(
    val name: String,
    val color: String? = null,
)
