package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.util.UUID

data class CategoryDto(
    val id: UUID,
    val name: String,
    val color: String?,
    val createdAt: Instant,
)
