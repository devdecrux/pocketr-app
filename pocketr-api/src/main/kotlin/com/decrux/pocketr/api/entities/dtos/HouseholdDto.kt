package com.decrux.pocketr.api.entities.dtos

import java.time.Instant
import java.util.UUID

data class HouseholdDto(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
    val members: List<HouseholdMemberDto>,
)
