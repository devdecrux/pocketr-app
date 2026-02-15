package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.util.UUID

data class CreateHouseholdDto(
    val name: String,
)

data class HouseholdDto(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
    val members: List<HouseholdMemberDto>,
)

data class HouseholdSummaryDto(
    val id: UUID,
    val name: String,
    val role: String,
    val status: String,
    val createdAt: Instant,
)

data class HouseholdMemberDto(
    val userId: Long,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
    val status: String,
    val joinedAt: Instant?,
)

data class InviteMemberDto(
    val email: String,
)

data class ShareAccountDto(
    val accountId: UUID,
)

data class HouseholdAccountShareDto(
    val accountId: UUID,
    val accountName: String,
    val ownerEmail: String,
    val ownerFirstName: String?,
    val ownerLastName: String?,
    val sharedAt: Instant,
)
