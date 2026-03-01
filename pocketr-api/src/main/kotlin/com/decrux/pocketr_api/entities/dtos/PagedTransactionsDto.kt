package com.decrux.pocketr_api.entities.dtos

data class PagedTransactionsDto(
    val content: List<TransactionDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
