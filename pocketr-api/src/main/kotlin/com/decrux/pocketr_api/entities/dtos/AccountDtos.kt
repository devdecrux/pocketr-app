package com.decrux.pocketr_api.entities.dtos

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateAccountDto(
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinor: Long? = null,
    val openingBalanceDate: LocalDate? = null,
)

data class UpdateAccountDto(
    val name: String? = null,
)

data class AccountDto(
    val id: UUID,
    val ownerUserId: Long,
    val name: String,
    val type: String,
    val currency: String,
    val createdAt: Instant,
)
