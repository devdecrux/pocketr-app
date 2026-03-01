package com.decrux.pocketr_api.entities.dtos

data class UpdateCategoryDto(
    val name: String,
    val color: String? = null,
)
