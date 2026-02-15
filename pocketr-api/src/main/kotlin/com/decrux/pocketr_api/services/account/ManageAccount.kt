package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import java.util.UUID

interface ManageAccount {

    fun createAccount(dto: CreateAccountDto, owner: User): AccountDto

    fun listAccounts(owner: User, includeArchived: Boolean = false): List<AccountDto>

    fun updateAccount(id: UUID, dto: UpdateAccountDto, owner: User): AccountDto
}
