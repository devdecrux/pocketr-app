package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.BalanceDto;
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto;
import com.decrux.pocketr.api.entities.dtos.PagedTransactionsDto;
import com.decrux.pocketr.api.entities.dtos.TransactionDto;
import com.decrux.pocketr.api.services.ledger.ManageLedger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ledger")
public class LedgerController {

    private final ManageLedger manageLedger;

    public LedgerController(ManageLedger manageLedger) {
        this.manageLedger = manageLedger;
    }

    @GetMapping("/transactions")
    public PagedTransactionsDto listTransactions(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) UUID householdId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return manageLedger.listTransactions(user, mode, householdId, dateFrom, dateTo, accountId, categoryId, page, size);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto createTransaction(
            @RequestBody CreateTransactionDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageLedger.createTransaction(dto, user);
    }

    @GetMapping("/accounts/{id}/balance")
    public BalanceDto getAccountBalance(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) UUID householdId,
            @AuthenticationPrincipal User user
    ) {
        return manageLedger.getAccountBalance(id, asOf != null ? asOf : LocalDate.now(), user, householdId);
    }

    @GetMapping("/accounts/balances")
    public List<BalanceDto> getAccountBalances(
            @RequestParam List<UUID> accountIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) UUID householdId,
            @AuthenticationPrincipal User user
    ) {
        return manageLedger.getAccountBalances(accountIds, asOf != null ? asOf : LocalDate.now(), user, householdId);
    }
}
