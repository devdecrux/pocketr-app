package com.decrux.pocketr.api.entities.dtos

import java.time.Instant
import java.util.UUID

data class HouseholdSummaryDto(
    val id: UUID,
    val name: String,
    val role: String,
    val status: String,
    val createdAt: Instant,
)
