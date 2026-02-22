package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.util.UUID

data class CreateCategoryDto(
    val name: String,
    val color: String? = null,
)

data class UpdateCategoryDto(
    val name: String,
    val color: String? = null,
)

data class CategoryDto(
    val id: UUID,
    val name: String,
    val color: String?,
    val createdAt: Instant,
)
