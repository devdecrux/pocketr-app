package com.decrux.pocketr.api.services.account

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.AccountDto
import com.decrux.pocketr.api.entities.dtos.CreateAccountDto
import com.decrux.pocketr.api.entities.dtos.UpdateAccountDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.exceptions.ForbiddenException
import com.decrux.pocketr.api.exceptions.NotFoundException
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.repositories.HouseholdAccountShareRepository
import com.decrux.pocketr.api.repositories.LedgerSplitRepository
import com.decrux.pocketr.api.services.OwnershipGuard
import com.decrux.pocketr.api.services.household.ManageHousehold
import com.decrux.pocketr.api.services.ledger.CurrentBalanceSnapshotReadiness
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ManageAccountImpl(
    private val accountRepository: AccountRepository,
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    private val ledgerSplitRepository: LedgerSplitRepository,
    private val currentBalanceSnapshotReadiness: CurrentBalanceSnapshotReadiness,
    private val currencyRepository: CurrencyRepository,
    private val openingBalanceService: OpeningBalanceService,
    private val manageHousehold: ManageHousehold,
    private val householdAccountShareRepository: HouseholdAccountShareRepository,
    private val ownershipGuard: OwnershipGuard,
) : ManageAccount {
    @Transactional(readOnly = true)
    override fun listIndividualAccounts(owner: User): List<AccountDto> {
        val userId = requireNotNull(owner.userId) { "User ID must not be null" }
        val accounts = accountRepository.findByOwnerUserIdAndStatus(userId, AccountStatus.ACTIVE)
        return accounts.map { it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun listAccountsByMode(
        user: User,
        mode: String,
        householdId: UUID?,
    ): List<AccountDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        return when (mode) {
            "INDIVIDUAL" -> listIndividualAccounts(user)
            "HOUSEHOLD" -> listHouseholdAccounts(userId, householdId)
            else -> throw BadRequestException("Invalid mode: $mode")
        }
    }

    private fun listHouseholdAccounts(
        userId: Long,
        householdId: UUID?,
    ): List<AccountDto> {
        val hhId =
            householdId
                ?: throw BadRequestException("householdId is required for HOUSEHOLD mode")

        if (!manageHousehold.isActiveMember(hhId, userId)) {
            throw ForbiddenException("Not an active member of this household")
        }

        val ownedAccounts = accountRepository.findByOwnerUserIdAndStatus(userId, AccountStatus.ACTIVE)
        val sharedAccountIds = householdAccountShareRepository.findSharedAccountIdsByHouseholdId(hhId)
        val sharedAccounts =
            if (sharedAccountIds.isNotEmpty()) {
                accountRepository.findAllById(sharedAccountIds).filter { it.status == AccountStatus.ACTIVE }
            } else {
                emptyList()
            }

        // A user's own account can also be shared with the household, so keep the owned entry once.
        val uniqueAccounts =
            (ownedAccounts + sharedAccounts).distinctBy { account ->
                requireNotNull(account.id) { "Account ID must not be null" }
            }

        return uniqueAccounts.map { it.toDto() }
    }

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
        if (openingBalanceMinor != 0L && accountType !in BALANCE_SHEET_TYPES) {
            throw BadRequestException(
                "openingBalanceMinor is supported only for ASSET and LIABILITY accounts",
            )
        }
        if (openingBalanceMinor == 0L && dto.openingBalanceDate != null) {
            throw BadRequestException(
                "openingBalanceDate requires non-zero openingBalanceMinor",
            )
        }
        if (accountType == AccountType.LIABILITY && openingBalanceMinor < 0L) {
            throw BadRequestException("Opening debt must be positive for LIABILITY accounts")
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
            openingBalanceService.createForNewAccount(
                owner = owner,
                account = savedAccount,
                openingBalanceMinor = openingBalanceMinor,
                txnDate = dto.openingBalanceDate ?: LocalDate.now(),
            )
        }

        return savedAccount.toDto()
    }

    @Transactional
    override fun updateAccount(
        id: UUID,
        dto: UpdateAccountDto,
        owner: User,
    ): AccountDto {
        val account =
            accountRepository
                .findOneById(id)
                .orElseThrow { NotFoundException("Account not found") }

        ownershipGuard.requireOwner(account.owner?.userId, requireNotNull(owner.userId), "Not the owner of this account")

        if (account.status == AccountStatus.ARCHIVED) {
            throw BadRequestException("Archived accounts cannot be updated")
        }

        dto.name?.let { account.name = it.trim() }
        return accountRepository.save(account).toDto()
    }

    @Transactional
    override fun archiveAccount(
        id: UUID,
        owner: User,
    ) {
        val account =
            accountRepository
                .findOneById(id)
                .orElseThrow { NotFoundException("Account not found") }

        ownershipGuard.requireOwner(account.owner?.userId, requireNotNull(owner.userId), "Not the owner of this account")

        if (account.status == AccountStatus.ARCHIVED) {
            checkNotNull(account.archivedAt) { "Archived account must have an archive timestamp" }
            return
        }
        check(account.archivedAt == null) { "Active account cannot have an archive timestamp" }
        if (account.type == AccountType.EQUITY) {
            throw BadRequestException("EQUITY accounts are system-managed and cannot be archived")
        }
        if (account.type in BALANCE_SHEET_TYPES && resolveArchiveBalance(id) != 0L) {
            throw BadRequestException("${account.type} accounts must have a zero balance before they can be archived")
        }

        account.archive(Instant.now())
        accountRepository.save(account)
    }

    private fun resolveArchiveBalance(accountId: UUID): Long {
        if (!currentBalanceSnapshotReadiness.isSnapshotAllowed(accountId)) {
            return ledgerSplitRepository.computeLifetimeBalance(accountId, SplitSide.DEBIT, SplitSide.CREDIT)
        }

        return accountCurrentBalanceRepository
            .findById(accountId)
            .map { it.rawBalanceMinor }
            .orElse(0L)
    }

    private companion object {
        val BALANCE_SHEET_TYPES = setOf(AccountType.ASSET, AccountType.LIABILITY)

        fun Account.toDto() =
            AccountDto(
                id = requireNotNull(id) { "Account ID must not be null" },
                ownerUserId = requireNotNull(owner?.userId) { "Owner user ID must not be null" },
                name = name,
                type = type.name,
                currency = requireNotNull(currency?.code) { "Currency must not be null" },
                status = status.name,
                archivedAt = archivedAt,
                createdAt = createdAt,
            )
    }
}
