package com.decrux.pocketr_api.entities.dtos

import java.util.UUID

data class CreateSplitDto(
    val accountId: UUID,
    val side: String,
    val amountMinor: Long,
    val categoryTagId: UUID? = null,
)
