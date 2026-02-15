package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr_api.services.account.ManageAccount
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/accounts")
class AccountController(
    private val manageAccount: ManageAccount,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(
        @RequestBody dto: CreateAccountDto,
        @AuthenticationPrincipal user: User,
    ): AccountDto {
        return manageAccount.createAccount(dto, user)
    }

    @GetMapping
    fun listAccounts(
        @RequestParam(defaultValue = "false") includeArchived: Boolean,
        @AuthenticationPrincipal user: User,
    ): List<AccountDto> {
        return manageAccount.listAccounts(user, includeArchived)
    }

    @PatchMapping("/{id}")
    fun updateAccount(
        @PathVariable id: UUID,
        @RequestBody dto: UpdateAccountDto,
        @AuthenticationPrincipal user: User,
    ): AccountDto {
        return manageAccount.updateAccount(id, dto, user)
    }
}
