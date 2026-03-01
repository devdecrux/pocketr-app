package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr_api.services.account.ManageAccount
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/accounts")
class AccountController(
    private val manageAccount: ManageAccount,
) {
    @GetMapping
    fun listAccounts(
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "INDIVIDUAL") mode: String,
        @RequestParam(required = false) householdId: UUID?,
    ): List<AccountDto> = manageAccount.listAccounts(user, mode, householdId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(
        @RequestBody dto: CreateAccountDto,
        @AuthenticationPrincipal user: User,
    ): AccountDto = manageAccount.createAccount(dto, user)

    @PatchMapping("/{id}")
    fun updateAccount(
        @PathVariable id: UUID,
        @RequestBody dto: UpdateAccountDto,
        @AuthenticationPrincipal user: User,
    ): AccountDto = manageAccount.updateAccount(id, dto, user)
}
