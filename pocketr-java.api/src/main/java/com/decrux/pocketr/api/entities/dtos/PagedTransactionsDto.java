package com.decrux.pocketr.api.entities.dtos;

import java.util.List;

public record PagedTransactionsDto(
    List<TransactionDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public List<TransactionDto> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
