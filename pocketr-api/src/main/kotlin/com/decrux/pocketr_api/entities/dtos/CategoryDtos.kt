package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.util.UUID

data class CreateCategoryDto(
    val name: String,
)

data class UpdateCategoryDto(
    val name: String,
)

data class CategoryDto(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
)
