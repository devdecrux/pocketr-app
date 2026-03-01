package com.decrux.pocketr.api.entities.dtos

data class CreateCategoryDto(
    val name: String,
    val color: String? = null,
)
