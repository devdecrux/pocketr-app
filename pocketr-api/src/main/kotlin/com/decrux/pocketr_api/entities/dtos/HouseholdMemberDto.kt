package com.decrux.pocketr_api.entities.dtos

import java.time.Instant

data class HouseholdMemberDto(
    val userId: Long,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    val status: String,
    val joinedAt: Instant?,
)
