package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.util.UUID

data class CreateAccountDto(
    val name: String,
    val type: String,
    val currency: String,
)

data class UpdateAccountDto(
    val name: String? = null,
    val isArchived: Boolean? = null,
)

data class AccountDto(
    val id: UUID,
    val name: String,
    val type: String,
    val currency: String,
    val isArchived: Boolean,
    val createdAt: Instant,
)
