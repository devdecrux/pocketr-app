package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.CurrencyRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ManageAccountImpl(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
) : ManageAccount {

    @Transactional
    override fun createAccount(dto: CreateAccountDto, owner: User): AccountDto {
        val accountType = try {
            AccountType.valueOf(dto.type)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account type: ${dto.type}")
        }

        val currency = currencyRepository.findById(dto.currency)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency: ${dto.currency}") }

        val account = Account(
            owner = owner,
            name = dto.name.trim(),
            type = accountType,
            currency = currency,
        )

        return accountRepository.save(account).toDto()
    }

    @Transactional(readOnly = true)
    override fun listAccounts(owner: User): List<AccountDto> {
        val userId = requireNotNull(owner.userId) { "User ID must not be null" }
        val accounts = accountRepository.findByOwnerUserId(userId)
        return accounts.map { it.toDto() }
    }

    @Transactional
    override fun updateAccount(id: UUID, dto: UpdateAccountDto, owner: User): AccountDto {
        val account = accountRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found") }

        if (account.owner?.userId != owner.userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this account")
        }

        dto.name?.let { account.name = it.trim() }
        return accountRepository.save(account).toDto()
    }

    private companion object {
        fun Account.toDto() = AccountDto(
            id = requireNotNull(id) { "Account ID must not be null" },
            ownerUserId = requireNotNull(owner?.userId) { "Owner user ID must not be null" },
            name = name,
            type = type.name,
            currency = requireNotNull(currency?.code) { "Currency must not be null" },
            createdAt = createdAt,
        )
    }
}
