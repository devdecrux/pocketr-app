package com.decrux.pocketr_api.entities.dtos

import java.util.UUID

data class SplitDto(
    val id: UUID,
    val accountId: UUID,
    val accountName: String,
    val accountType: String,
    val side: String,
    val amountMinor: Long,
    val effectMinor: Long,
    val categoryTagId: UUID?,
    val categoryTagName: String?,
)
