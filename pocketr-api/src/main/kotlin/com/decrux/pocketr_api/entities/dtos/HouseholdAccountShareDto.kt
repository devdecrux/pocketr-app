package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.util.UUID

data class HouseholdAccountShareDto(
    val accountId: UUID,
    val accountName: String,
    val ownerEmail: String,
    val ownerFirstName: String?,
    val ownerLastName: String?,
    val sharedAt: Instant,
)
