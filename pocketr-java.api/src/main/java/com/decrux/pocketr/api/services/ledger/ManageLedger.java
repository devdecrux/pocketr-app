package com.decrux.pocketr.api.services.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.BalanceDto;
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto;
import com.decrux.pocketr.api.entities.dtos.PagedTransactionsDto;
import com.decrux.pocketr.api.entities.dtos.TransactionDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ManageLedger {

    TransactionDto createTransaction(CreateTransactionDto dto, User creator);

    PagedTransactionsDto listTransactions(
        User user,
        String mode,
        UUID householdId,
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID accountId,
        UUID categoryId,
        int page,
        int size
    );

    List<BalanceDto> getAccountBalances(List<UUID> accountIds, LocalDate asOf, User user, UUID householdId);

    BalanceDto getAccountBalance(UUID accountId, LocalDate asOf, User user, UUID householdId);
}
