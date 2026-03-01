package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateAccountDto
import com.decrux.pocketr_api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr_api.exceptions.BadRequestException
import com.decrux.pocketr_api.exceptions.ForbiddenException
import com.decrux.pocketr_api.exceptions.NotFoundException
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.CurrencyRepository
import com.decrux.pocketr_api.repositories.HouseholdAccountShareRepository
import com.decrux.pocketr_api.services.OwnershipGuard
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class ManageAccountImpl(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val openingBalanceService: OpeningBalanceService,
    private val manageHousehold: ManageHousehold,
    private val householdAccountShareRepository: HouseholdAccountShareRepository,
    private val ownershipGuard: OwnershipGuard,
) : ManageAccount {
    @Transactional
    override fun createAccount(
        dto: CreateAccountDto,
        owner: User,
    ): AccountDto {
        val accountType =
            try {
                AccountType.valueOf(dto.type)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("Invalid account type: ${dto.type}")
            }
        if (accountType == AccountType.EQUITY) {
            throw BadRequestException(
                "EQUITY accounts are system-managed and cannot be created manually",
            )
        }

        val currency =
            currencyRepository
                .findById(dto.currency)
                .orElseThrow { BadRequestException("Invalid currency: ${dto.currency}") }

        val openingBalanceMinor = dto.openingBalanceMinor ?: 0L
        if (openingBalanceMinor != 0L && accountType != AccountType.ASSET) {
            throw BadRequestException(
                "openingBalanceMinor is supported only for ASSET accounts",
            )
        }
        if (openingBalanceMinor == 0L && dto.openingBalanceDate != null) {
            throw BadRequestException(
                "openingBalanceDate requires non-zero openingBalanceMinor",
            )
        }

        val account =
            Account(
                owner = owner,
                name = dto.name.trim(),
                type = accountType,
                currency = currency,
            )

        val savedAccount = accountRepository.save(account)

        if (openingBalanceMinor != 0L) {
            openingBalanceService.createForNewAssetAccount(
                owner = owner,
                assetAccount = savedAccount,
                openingBalanceMinor = openingBalanceMinor,
                txnDate = dto.openingBalanceDate ?: LocalDate.now(),
            )
        }

        return savedAccount.toDto()
    }

    @Transactional(readOnly = true)
    override fun listAccounts(owner: User): List<AccountDto> {
        val userId = requireNotNull(owner.userId) { "User ID must not be null" }
        val accounts = accountRepository.findByOwnerUserId(userId)
        return accounts.map { it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun listAccounts(
        user: User,
        mode: String,
        householdId: UUID?,
    ): List<AccountDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        if (mode == "INDIVIDUAL") {
            return listAccounts(user)
        }

        if (mode != "HOUSEHOLD") {
            throw BadRequestException("Invalid mode: $mode")
        }

        val hhId =
            householdId
                ?: throw BadRequestException("householdId is required for HOUSEHOLD mode")

        if (!manageHousehold.isActiveMember(hhId, userId)) {
            throw ForbiddenException("Not an active member of this household")
        }

        val ownedAccounts = accountRepository.findByOwnerUserId(userId)
        val sharedAccountIds = householdAccountShareRepository.findSharedAccountIdsByHouseholdId(hhId)
        val sharedAccounts =
            if (sharedAccountIds.isNotEmpty()) {
                accountRepository.findAllById(sharedAccountIds)
            } else {
                emptyList()
            }

        val seen = mutableSetOf<UUID>()
        val merged = mutableListOf<Account>()
        for (account in ownedAccounts + sharedAccounts) {
            val accountId = requireNotNull(account.id)
            if (seen.add(accountId)) {
                merged.add(account)
            }
        }

        return merged.map { it.toDto() }
    }

    @Transactional
    override fun updateAccount(
        id: UUID,
        dto: UpdateAccountDto,
        owner: User,
    ): AccountDto {
        val account =
            accountRepository
                .findById(id)
                .orElseThrow { NotFoundException("Account not found") }

        ownershipGuard.requireOwner(account.owner?.userId, requireNotNull(owner.userId), "Not the owner of this account")

        dto.name?.let { account.name = it.trim() }
        return accountRepository.save(account).toDto()
    }

    private companion object {
        fun Account.toDto() =
            AccountDto(
                id = requireNotNull(id) { "Account ID must not be null" },
                ownerUserId = requireNotNull(owner?.userId) { "Owner user ID must not be null" },
                name = name,
                type = type.name,
                currency = requireNotNull(currency?.code) { "Currency must not be null" },
                createdAt = createdAt,
            )
    }
}
