package com.decrux.pocketr.api.entities.dtos

import java.time.Instant
import java.util.UUID

data class AccountDto(
    val id: UUID,
    val ownerUserId: Long,
    val name: String,
    val type: String,
    val currency: String,
    val createdAt: Instant,
)
