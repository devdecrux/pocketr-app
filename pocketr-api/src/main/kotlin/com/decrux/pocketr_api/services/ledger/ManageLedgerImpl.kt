package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.*
import com.decrux.pocketr_api.entities.dtos.BalanceDto
import com.decrux.pocketr_api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr_api.entities.dtos.SplitDto
import com.decrux.pocketr_api.entities.dtos.TransactionDto
import com.decrux.pocketr_api.repositories.*
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

@Service
class ManageLedgerImpl(
    private val ledgerTxnRepository: LedgerTxnRepository,
    private val ledgerSplitRepository: LedgerSplitRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryTagRepository: CategoryTagRepository,
    private val manageHousehold: ManageHousehold,
) : ManageLedger {

    @Transactional
    override fun createTransaction(dto: CreateTransactionDto, creator: User): TransactionDto {
        val userId = requireNotNull(creator.userId) { "User ID must not be null" }
        val isHouseholdMode = dto.mode?.uppercase() == "HOUSEHOLD"

        // 1. Require at least 2 splits
        if (dto.splits.size < 2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction must have at least 2 splits")
        }

        // 2. Validate all amounts > 0
        dto.splits.forEach { split ->
            if (split.amountMinor <= 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "All split amounts must be greater than 0")
            }
        }

        // 3. Validate split sides
        dto.splits.forEach { split ->
            try {
                SplitSide.valueOf(split.side)
            } catch (_: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid split side: ${split.side}")
            }
        }

        // 4. Double-entry: sum(DEBIT) == sum(CREDIT)
        val sumDebits = dto.splits.filter { it.side == "DEBIT" }.sumOf { it.amountMinor }
        val sumCredits = dto.splits.filter { it.side == "CREDIT" }.sumOf { it.amountMinor }
        if (sumDebits != sumCredits) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Double-entry violation: sum of debits ($sumDebits) must equal sum of credits ($sumCredits)",
            )
        }

        // 5. Validate currency exists
        val currency = currencyRepository.findById(dto.currency)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency: ${dto.currency}") }

        // 6. Load and validate all accounts
        val accountIds = dto.splits.map { it.accountId }.distinct()
        val accounts = accountRepository.findAllById(accountIds)
        if (accounts.size != accountIds.size) {
            val foundIds = accounts.map { it.id }.toSet()
            val missingIds = accountIds.filter { it !in foundIds }
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Accounts not found: $missingIds")
        }
        val accountMap = accounts.associateBy { requireNotNull(it.id) }

        // 7. Currency consistency: all split accounts must match transaction currency
        accounts.forEach { account ->
            if (account.currency?.code != dto.currency) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Account '${account.name}' has currency ${account.currency?.code} but transaction currency is ${dto.currency}",
                )
            }
        }

        // 8. Permission check: individual vs household mode
        val ownedAccounts = accounts.filter { it.owner?.userId == userId }
        val nonOwnedAccounts = accounts.filter { it.owner?.userId != userId }

        if (nonOwnedAccounts.isNotEmpty()) {
            // Cross-user transaction requires household mode
            if (!isHouseholdMode) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cannot post to accounts not owned by current user in individual mode",
                )
            }

            val householdId = dto.householdId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "householdId is required for household mode")

            // Verify creator is active member of household
            if (!manageHousehold.isActiveMember(householdId, userId)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not an active member of this household")
            }

            // Verify all non-owned accounts are shared into the household
            nonOwnedAccounts.forEach { account ->
                val accountId = requireNotNull(account.id)
                if (!manageHousehold.isAccountShared(householdId, accountId)) {
                    throw ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Account '${account.name}' is not shared into household",
                    )
                }
            }

            // Cross-user transfer rule: all accounts must be ASSET only (v1)
            accounts.forEach { account ->
                if (account.type != AccountType.ASSET) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cross-user transfers only allow ASSET accounts (v1), but '${account.name}' is ${account.type}",
                    )
                }
            }
        }

        // 9. Validate category tags
        val categoryTagIds = dto.splits.mapNotNull { it.categoryTagId }.distinct()
        val categoryTagMap: Map<UUID, CategoryTag> = if (categoryTagIds.isNotEmpty()) {
            val tags = categoryTagRepository.findAllById(categoryTagIds)
            if (tags.size != categoryTagIds.size) {
                val foundIds = tags.map { it.id }.toSet()
                val missingIds = categoryTagIds.filter { it !in foundIds }
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Category tags not found: $missingIds")
            }
            tags.forEach { tag ->
                if (tag.owner?.userId != userId) {
                    throw ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Category tag '${tag.name}' is not owned by current user",
                    )
                }
            }
            tags.associateBy { requireNotNull(it.id) }
        } else {
            emptyMap()
        }

        // 10. Persist transaction
        val txn = LedgerTxn(
            createdBy = creator,
            householdId = if (isHouseholdMode) dto.householdId else null,
            txnDate = dto.txnDate,
            description = dto.description.trim(),
            currency = currency,
        )

        val splits = dto.splits.map { splitDto ->
            LedgerSplit(
                transaction = txn,
                account = accountMap.getValue(splitDto.accountId),
                side = SplitSide.valueOf(splitDto.side),
                amountMinor = splitDto.amountMinor,
                categoryTag = splitDto.categoryTagId?.let { categoryTagMap[it] },
                memo = splitDto.memo?.trim()?.ifBlank { null },
            )
        }
        txn.splits = splits.toMutableList()

        val savedTxn = ledgerTxnRepository.save(txn)
        return savedTxn.toDto()
    }

    @Transactional(readOnly = true)
    override fun listTransactions(
        user: User,
        mode: String?,
        householdId: UUID?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        accountId: UUID?,
        categoryId: UUID?,
    ): List<TransactionDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        val isHouseholdMode = mode?.uppercase() == "HOUSEHOLD"

        var spec: Specification<LedgerTxn> = if (isHouseholdMode) {
            val hhId = householdId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "householdId is required for household mode")
            if (!manageHousehold.isActiveMember(hhId, userId)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not an active member of this household")
            }
            LedgerTxnSpecs.forHousehold(hhId)
        } else {
            LedgerTxnSpecs.forUser(userId)
        }

        dateFrom?.let { spec = spec.and(LedgerTxnSpecs.dateFrom(it)) }
        dateTo?.let { spec = spec.and(LedgerTxnSpecs.dateTo(it)) }
        accountId?.let { spec = spec.and(LedgerTxnSpecs.hasAccount(it)) }
        categoryId?.let { spec = spec.and(LedgerTxnSpecs.hasCategory(it)) }

        return ledgerTxnRepository.findAll(spec).map { it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun getAccountBalance(accountId: UUID, asOf: LocalDate, user: User, householdId: UUID?): BalanceDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        val account = accountRepository.findById(accountId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found") }

        // In household mode, allow viewing shared accounts; otherwise require ownership
        if (householdId != null) {
            if (!manageHousehold.isActiveMember(householdId, userId)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not an active member of this household")
            }
            if (!manageHousehold.isAccountShared(householdId, accountId)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not shared into this household")
            }
        } else if (account.owner?.userId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this account")
        }

        val isDebitNormal = account.type in setOf(AccountType.ASSET, AccountType.EXPENSE)
        val balanceMinor = if (isDebitNormal) {
            ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)
        } else {
            ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)
        }

        return BalanceDto(
            accountId = requireNotNull(account.id),
            accountName = account.name,
            accountType = account.type.name,
            currency = requireNotNull(account.currency?.code),
            balanceMinor = balanceMinor,
            asOf = asOf,
        )
    }

    private companion object {
        val DEBIT_NORMAL_TYPES = setOf(AccountType.ASSET, AccountType.EXPENSE)
        val TRANSFER_TYPES = setOf(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY)

        fun LedgerTxn.toDto(): TransactionDto {
            val splitDtos = splits.map { it.toDto() }
            return TransactionDto(
                id = requireNotNull(id) { "Transaction ID must not be null" },
                txnDate = txnDate,
                currency = requireNotNull(currency?.code) { "Currency must not be null" },
                description = description,
                householdId = householdId,
                txnKind = deriveTxnKind(splits),
                splits = splitDtos,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

        fun deriveTxnKind(splits: List<LedgerSplit>): String {
            val accountTypes = splits.mapNotNull { it.account?.type }.toSet()
            return when {
                accountTypes.all { it in TRANSFER_TYPES } -> "TRANSFER"
                accountTypes.any { it == AccountType.EXPENSE } -> "EXPENSE"
                accountTypes.any { it == AccountType.INCOME } -> "INCOME"
                else -> "TRANSFER"
            }
        }

        fun LedgerSplit.toDto(): SplitDto {
            val accountType = requireNotNull(account?.type) { "Account type must not be null" }
            val isDebitNormal = accountType in DEBIT_NORMAL_TYPES
            val increases = (isDebitNormal && side == SplitSide.DEBIT) || (!isDebitNormal && side == SplitSide.CREDIT)
            val effectMinor = if (increases) amountMinor else -amountMinor

            return SplitDto(
                id = requireNotNull(id) { "Split ID must not be null" },
                accountId = requireNotNull(account?.id) { "Account must not be null" },
                accountName = requireNotNull(account?.name) { "Account name must not be null" },
                accountType = accountType.name,
                side = side.name,
                amountMinor = amountMinor,
                effectMinor = effectMinor,
                categoryTagId = categoryTag?.id,
                categoryTagName = categoryTag?.name,
                memo = memo,
            )
        }
    }
}
