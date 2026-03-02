package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateAccountDto;
import com.decrux.pocketr.api.entities.dtos.UpdateAccountDto;
import com.decrux.pocketr.api.services.account.ManageAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final ManageAccount manageAccount;

    public AccountController(ManageAccount manageAccount) {
        this.manageAccount = manageAccount;
    }

    @GetMapping
    public List<AccountDto> listAccounts(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "INDIVIDUAL") String mode,
            @RequestParam(required = false) UUID householdId
    ) {
        return manageAccount.listAccountsByMode(user, mode, householdId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto createAccount(
            @RequestBody CreateAccountDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageAccount.createAccount(dto, user);
    }

    @PatchMapping("/{id}")
    public AccountDto updateAccount(
            @PathVariable UUID id,
            @RequestBody UpdateAccountDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageAccount.updateAccount(id, dto, user);
    }
}
