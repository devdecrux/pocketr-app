package com.decrux.pocketr_api.entities.dtos

data class CreateCategoryDto(
    val name: String,
    val color: String? = null,
)
