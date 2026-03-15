package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.BalanceDto
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr.api.entities.dtos.PagedTransactionsDto
import com.decrux.pocketr.api.entities.dtos.SplitDto
import com.decrux.pocketr.api.entities.dtos.TransactionDto
import com.decrux.pocketr.api.entities.dtos.TxnCreatorDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.exceptions.ForbiddenException
import com.decrux.pocketr.api.exceptions.NotFoundException
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.CategoryTagRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.repositories.LedgerSplitRepository
import com.decrux.pocketr.api.repositories.LedgerTxnRepository
import com.decrux.pocketr.api.services.household.ManageHousehold
import com.decrux.pocketr.api.services.ledger.validations.CrossUserAssetAccountTypeValidator
import com.decrux.pocketr.api.services.ledger.validations.DoubleEntryBalanceValidator
import com.decrux.pocketr.api.services.ledger.validations.HouseholdIdPresenceValidator
import com.decrux.pocketr.api.services.ledger.validations.HouseholdMembershipValidator
import com.decrux.pocketr.api.services.ledger.validations.HouseholdSharedAccountValidator
import com.decrux.pocketr.api.services.ledger.validations.IndividualModeOwnershipValidator
import com.decrux.pocketr.api.services.ledger.validations.MinimumSplitCountValidator
import com.decrux.pocketr.api.services.ledger.validations.PositiveSplitAmountValidator
import com.decrux.pocketr.api.services.ledger.validations.SplitSideValueValidator
import com.decrux.pocketr.api.services.ledger.validations.TransactionAccountCurrencyValidator
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class ManageLedgerImpl(
    private val ledgerTxnRepository: LedgerTxnRepository,
    private val ledgerSplitRepository: LedgerSplitRepository,
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryTagRepository: CategoryTagRepository,
    private val manageHousehold: ManageHousehold,
    private val userAvatarService: UserAvatarService,
    private val minimumSplitCountValidator: MinimumSplitCountValidator,
    private val positiveSplitAmountValidator: PositiveSplitAmountValidator,
    private val splitSideValueValidator: SplitSideValueValidator,
    private val doubleEntryBalanceValidator: DoubleEntryBalanceValidator,
    private val transactionAccountCurrencyValidator: TransactionAccountCurrencyValidator,
    private val individualModeOwnershipValidator: IndividualModeOwnershipValidator,
    private val householdIdPresenceValidator: HouseholdIdPresenceValidator,
    private val householdMembershipValidator: HouseholdMembershipValidator,
    private val householdSharedAccountValidator: HouseholdSharedAccountValidator,
    private val crossUserAssetAccountTypeValidator: CrossUserAssetAccountTypeValidator,
    @Value("\${ledger.accounts.snapshot.balance.enabled:false}")
    private val currentBalanceSnapshotEnabled: Boolean,
    private val currentBalanceSnapshotReadiness: CurrentBalanceSnapshotReadiness,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ManageLedger {
    @Transactional
    override fun createTransaction(
        dto: CreateTransactionDto,
        creator: User,
    ): TransactionDto {
        val userId = requireNotNull(creator.userId) { "User ID must not be null" }
        val isHouseholdMode = dto.mode?.uppercase() == "HOUSEHOLD"

        // 1-4. Validate splits (count, amounts, sides, double-entry balance)
        minimumSplitCountValidator.validate(dto.splits)
        positiveSplitAmountValidator.validate(dto.splits)
        splitSideValueValidator.validate(dto.splits)
        doubleEntryBalanceValidator.validate(dto.splits)

        // 5. Validate currency exists
        val currency =
            currencyRepository
                .findById(dto.currency)
                .orElseThrow { BadRequestException("Invalid currency: ${dto.currency}") }

        // 6. Load and validate all accounts
        val accountIds = dto.splits.map { it.accountId }.distinct()
        val accounts = accountRepository.findAllById(accountIds)
        if (accounts.size != accountIds.size) {
            val foundIds = accounts.map { it.id }.toSet()
            val missingIds = accountIds.filter { it !in foundIds }
            throw BadRequestException("Accounts not found: $missingIds")
        }
        val accountMap = accounts.associateBy { requireNotNull(it.id) }

        // 7. Currency consistency
        transactionAccountCurrencyValidator.validate(accounts, dto.currency)

        // 8. Permission check: individual vs household mode
        val nonOwnedAccounts = accounts.filter { it.owner?.userId != userId }
        if (nonOwnedAccounts.isNotEmpty()) {
            individualModeOwnershipValidator.validate(nonOwnedAccounts, isHouseholdMode)
            val hhId = householdIdPresenceValidator.validate(dto.householdId)
            householdMembershipValidator.validate(manageHousehold, hhId, userId)
            householdSharedAccountValidator.validate(nonOwnedAccounts, manageHousehold, hhId)
            crossUserAssetAccountTypeValidator.validate(accounts, dto.splits, userId)
        }

        // 9. Validate category tags
        val categoryTagIds = dto.splits.mapNotNull { it.categoryTagId }.distinct()
        val categoryTagMap: Map<UUID, CategoryTag> =
            if (categoryTagIds.isNotEmpty()) {
                val tags = categoryTagRepository.findAllById(categoryTagIds)
                if (tags.size != categoryTagIds.size) {
                    val foundIds = tags.map { it.id }.toSet()
                    val missingIds = categoryTagIds.filter { it !in foundIds }
                    throw BadRequestException("Category tags not found: $missingIds")
                }
                tags.forEach { tag ->
                    if (tag.owner?.userId != userId) {
                        throw ForbiddenException(
                            "Category tag '${tag.name}' is not owned by current user",
                        )
                    }
                }
                tags.associateBy { requireNotNull(it.id) }
            } else {
                emptyMap()
            }

        // 10. Persist transaction
        val txn =
            LedgerTxn(
                createdBy = creator,
                householdId = if (isHouseholdMode) dto.householdId else null,
                txnDate = dto.txnDate,
                description = dto.description.trim(),
                currency = currency,
            )

        val splits =
            dto.splits.map { splitDto ->
                LedgerSplit(
                    transaction = txn,
                    account = accountMap.getValue(splitDto.accountId),
                    side = SplitSide.valueOf(splitDto.side),
                    amountMinor = splitDto.amountMinor,
                    categoryTag = splitDto.categoryTagId?.let { categoryTagMap[it] },
                )
            }
        txn.splits = splits.toMutableList()

        val savedTxn = ledgerTxnRepository.save(txn)
        applyCurrentBalanceProjection(savedTxn.splits)
        return savedTxn.toDto(userAvatarService)
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
        page: Int,
        size: Int,
    ): PagedTransactionsDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        val isHouseholdMode = mode?.uppercase() == "HOUSEHOLD"

        var spec: Specification<LedgerTxn> =
            if (isHouseholdMode) {
                val hhId =
                    householdId
                        ?: throw BadRequestException("householdId is required for household mode")
                if (!manageHousehold.isActiveMember(hhId, userId)) {
                    throw ForbiddenException("Not an active member of this household")
                }
                val sharedAccountIds = manageHousehold.getSharedAccountIds(hhId)
                if (sharedAccountIds.isEmpty()) {
                    return PagedTransactionsDto(content = emptyList(), page = page, size = size, totalElements = 0, totalPages = 0)
                }
                LedgerTxnSpecs.hasAnySharedAccount(sharedAccountIds)
            } else {
                LedgerTxnSpecs.forUser(userId)
            }

        dateFrom?.let { spec = spec.and(LedgerTxnSpecs.dateFrom(it)) }
        dateTo?.let { spec = spec.and(LedgerTxnSpecs.dateTo(it)) }
        accountId?.let { spec = spec.and(LedgerTxnSpecs.hasAccount(it)) }
        categoryId?.let { spec = spec.and(LedgerTxnSpecs.hasCategory(it)) }

        val pageable =
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "txnDate").and(Sort.by(Sort.Direction.DESC, "createdAt")),
            )
        val pageResult = ledgerTxnRepository.findAll(spec, pageable)

        return PagedTransactionsDto(
            content = pageResult.content.map { it.toDto(userAvatarService) },
            page = pageResult.number,
            size = pageResult.size,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages,
        )
    }

    @Transactional(readOnly = true)
    override fun getAccountBalances(
        accountIds: List<UUID>,
        asOf: LocalDate,
        user: User,
        householdId: UUID?,
    ): List<BalanceDto> {
        if (accountIds.isEmpty()) {
            return emptyList()
        }

        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        val uniqueAccountIds = accountIds.distinct()
        val accounts = accountRepository.findAllById(uniqueAccountIds)
        if (accounts.size != uniqueAccountIds.size) {
            throw NotFoundException("Account not found")
        }

        if (householdId != null) {
            if (!manageHousehold.isActiveMember(householdId, userId)) {
                throw ForbiddenException("Not an active member of this household")
            }
            val sharedAccountIds = manageHousehold.getSharedAccountIds(householdId)
            if (uniqueAccountIds.any { it !in sharedAccountIds }) {
                throw ForbiddenException("Account is not shared into this household")
            }
        } else if (accounts.any { it.owner?.userId != userId }) {
            throw ForbiddenException("Not the owner of this account")
        }

        val rawBalancesByAccountId = resolveRawBalances(uniqueAccountIds, asOf)

        val accountById = accounts.associateBy { requireNotNull(it.id) }
        return uniqueAccountIds.map { accountId ->
            val account = accountById.getValue(accountId)
            val rawBalance = rawBalancesByAccountId[accountId] ?: 0L
            val balanceMinor = if (account.type in DEBIT_NORMAL_TYPES) rawBalance else -rawBalance

            BalanceDto(
                accountId = accountId,
                accountName = account.name,
                accountType = account.type.name,
                currency = requireNotNull(account.currency?.code),
                balanceMinor = balanceMinor,
                asOf = asOf,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getAccountBalance(
        accountId: UUID,
        asOf: LocalDate,
        user: User,
        householdId: UUID?,
    ): BalanceDto {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        val account =
            accountRepository
                .findById(accountId)
                .orElseThrow { NotFoundException("Account not found") }

        // In household mode, allow viewing shared accounts; otherwise require ownership
        if (householdId != null) {
            if (!manageHousehold.isActiveMember(householdId, userId)) {
                throw ForbiddenException("Not an active member of this household")
            }
            if (!manageHousehold.isAccountShared(householdId, accountId)) {
                throw ForbiddenException("Account is not shared into this household")
            }
        } else if (account.owner?.userId != userId) {
            throw ForbiddenException("Not the owner of this account")
        }

        val rawBalance = resolveRawBalance(accountId, asOf)
        val balanceMinor = if (account.type in DEBIT_NORMAL_TYPES) rawBalance else -rawBalance

        return BalanceDto(
            accountId = requireNotNull(account.id),
            accountName = account.name,
            accountType = account.type.name,
            currency = requireNotNull(account.currency?.code),
            balanceMinor = balanceMinor,
            asOf = asOf,
        )
    }

    private fun applyCurrentBalanceProjection(splits: List<LedgerSplit>) {
        val deltasByAccountId = accumulateDeltasByAccount(splits)
        val orderedDeltasByAccountId = deltasByAccountId.toSortedMap()

        try {
            // Apply projection deltas in a stable account-id order to avoid deadlocks when
            // concurrent transactions touch the same set of accounts through different split orders.
            orderedDeltasByAccountId.forEach { (accountId, delta) ->
                accountCurrentBalanceRepository.addDelta(accountId, delta)
            }
        } catch (ex: RuntimeException) {
            logger.error(
                "Failed to update snapshot balance delta for the transaction; rolling back the entire transaction due to {}",
                ex.message,
                ex,
            )
            throw ex
        }

        logger.debug(
            "Applied current balance snapshot updates for {} account(s) from {} split(s).",
            orderedDeltasByAccountId.size,
            splits.size,
        )
    }

    private fun accumulateDeltasByAccount(splits: List<LedgerSplit>): MutableMap<UUID, Long> {
        val deltasByAccountId = mutableMapOf<UUID, Long>()

        splits.forEach { split ->
            val accountId = split.requireAccountId()
            val signedDelta = split.toRawSignedDelta()
            val newComputedDelta =
                deltasByAccountId[accountId]
                    ?.let { currentDelta -> Math.addExact(currentDelta, signedDelta) }
                    ?: signedDelta

            if (newComputedDelta == 0L) {
                deltasByAccountId.remove(accountId)
            } else {
                deltasByAccountId[accountId] = newComputedDelta
            }
        }

        return deltasByAccountId
    }

    private fun LedgerSplit.requireAccountId(): UUID =
        requireNotNull(account?.id) { "Split account ID must not be null" }

    private fun LedgerSplit.toRawSignedDelta(): Long =
        when (side) {
            SplitSide.DEBIT -> amountMinor
            SplitSide.CREDIT -> -amountMinor
        }

    private fun resolveRawBalances(
        accountIds: Collection<UUID>,
        asOf: LocalDate,
    ): Map<UUID, Long> {
        val startedAt = System.nanoTime()
        if (!isSnapshotFeatureEligible(asOf)) {
            return computeRawBalancesFromLedger(
                accountIds = accountIds,
                asOf = asOf,
                reason = "snapshot balances are only available for today",
            )
        }

        val snapshotEligibleAccountIds =
            accountIds
                .asSequence()
                .filter { accountId -> currentBalanceSnapshotReadiness.isSnapshotAllowed(accountId) }
                .toList()
        if (snapshotEligibleAccountIds.isEmpty()) {
            return computeRawBalancesFromLedger(
                accountIds = accountIds,
                asOf = asOf,
                reason = "snapshots are not allowed for the requested accounts",
            )
        }

        val snapshotEligibleAccountId = snapshotEligibleAccountIds.toSet()
        val notSnapshotEligibleAccountId = accountIds.filterNot { it in snapshotEligibleAccountId }

        return try {
            val balancesByAccountId =
                accountCurrentBalanceRepository
                    .findAllByAccountIdIn(snapshotEligibleAccountIds)
                    .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }
                    .toMutableMap()

            if (notSnapshotEligibleAccountId.isNotEmpty()) {
                balancesByAccountId.putAll(computeRawBalancesFromLedger(notSnapshotEligibleAccountId, asOf))
                logger.debug(
                    "Resolved balances for {} account(s) as of {} using {} snapshot balance(s) and {} computed ledger balance(s). Took {} ms.",
                    accountIds.size,
                    asOf,
                    snapshotEligibleAccountIds.size,
                    notSnapshotEligibleAccountId.size,
                    elapsedMillis(startedAt),
                )
            } else {
                logger.debug(
                    "Resolved balances for {} account(s) as of {} using current balance snapshots. Took {} ms.",
                    accountIds.size,
                    asOf,
                    elapsedMillis(startedAt),
                )
            }

            balancesByAccountId
        } catch (ex: DataAccessException) {
            logger.warn(
                "Failed to read current balance snapshots for {} account(s) as of {}; falling back to computed ledger totals. Took {} ms.",
                accountIds.size,
                asOf,
                elapsedMillis(startedAt),
                ex,
            )
            computeRawBalancesFromLedger(accountIds, asOf)
        }
    }

    private fun resolveRawBalance(
        accountId: UUID,
        asOf: LocalDate,
    ): Long {
        val startedAt = System.nanoTime()
        if (!shouldUseSnapshotBalance(accountId, asOf)) {
            return computeRawBalanceFromLedger(
                accountId = accountId,
                asOf = asOf,
                reason = "snapshot balances are unavailable for this account and date",
            )
        }

        return try {
            logger.debug(
                "Resolved balance for account {} as of {} using the current balance snapshot. Took {} ms.",
                accountId,
                asOf,
                elapsedMillis(startedAt),
            )
            accountCurrentBalanceRepository
                .findById(accountId)
                .map { it.rawBalanceMinor }
                .orElse(0L)
        } catch (ex: DataAccessException) {
            logger.warn(
                "Failed to read the current balance snapshot for account {} as of {}; falling back to computed ledger totals. Took {} ms.",
                accountId,
                asOf,
                elapsedMillis(startedAt),
                ex,
            )
            computeRawBalanceFromLedger(accountId, asOf)
        }
    }

    private fun computeRawBalancesFromLedger(
        accountIds: Collection<UUID>,
        asOf: LocalDate,
        reason: String? = null,
    ): Map<UUID, Long> {
        val startedAt = System.nanoTime()
        val rawBalancesByAccountId =
            ledgerSplitRepository
                .computeRawBalancesByAccountIds(
                    accountIds,
                    asOf,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT,
                )
                .associate { it.accountId to it.rawBalance }

        if (reason != null) {
            logger.debug(
                "Resolved balances for {} account(s) as of {} using computed ledger totals because {}. Took {} ms.",
                accountIds.size,
                asOf,
                reason,
                elapsedMillis(startedAt),
            )
        }

        return rawBalancesByAccountId
    }

    private fun computeRawBalanceFromLedger(
        accountId: UUID,
        asOf: LocalDate,
        reason: String? = null,
    ): Long {
        val startedAt = System.nanoTime()
        val rawBalance = ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)

        if (reason != null) {
            logger.debug(
                "Resolved balance for account {} as of {} using computed ledger totals because {}. Took {} ms.",
                accountId,
                asOf,
                reason,
                elapsedMillis(startedAt),
            )
        }

        return rawBalance
    }

    private fun shouldUseSnapshotBalance(
        accountId: UUID,
        asOf: LocalDate,
    ): Boolean =
        isSnapshotFeatureEligible(asOf) &&
            currentBalanceSnapshotReadiness.isSnapshotAllowed(accountId)

    private fun isSnapshotFeatureEligible(asOf: LocalDate): Boolean =
        currentBalanceSnapshotEnabled &&
            asOf == LocalDate.now(clock)

    private fun elapsedMillis(startedAtNanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    private companion object {
        private val logger = LoggerFactory.getLogger(ManageLedgerImpl::class.java)
        val DEBIT_NORMAL_TYPES = setOf(AccountType.ASSET, AccountType.EXPENSE)
        val TRANSFER_TYPES = setOf(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY)

        fun LedgerTxn.toDto(avatarService: UserAvatarService): TransactionDto {
            val splitDtos = splits.map { it.toDto() }
            return TransactionDto(
                id = requireNotNull(id) { "Transaction ID must not be null" },
                txnDate = txnDate,
                currency = requireNotNull(currency?.code) { "Currency must not be null" },
                description = description,
                householdId = householdId,
                txnKind = deriveTxnKind(splits),
                createdBy =
                    createdBy?.let {
                        TxnCreatorDto(
                            firstName = it.firstName,
                            lastName = it.lastName,
                            email = it.email,
                            avatar = avatarService.resolveAvatarDataUrl(it.avatarPath),
                        )
                    },
                splits = splitDtos,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

        fun deriveTxnKind(splits: List<LedgerSplit>): String {
            val accountTypes = splits.mapNotNull { it.account?.type }.toSet()
            return when {
                isOpeningBalanceEntry(splits, accountTypes) -> "OPENING_BALANCE"
                isOpeningDebtEntry(splits, accountTypes) -> "OPENING_DEBT"
                isDebtPayment(splits) -> "DEBT_PAYMENT"
                accountTypes.all { it in TRANSFER_TYPES } -> "TRANSFER"
                accountTypes.any { it == AccountType.EXPENSE } -> "EXPENSE"
                accountTypes.any { it == AccountType.INCOME } -> "INCOME"
                else -> "TRANSFER"
            }
        }

        fun isOpeningBalanceEntry(
            splits: List<LedgerSplit>,
            accountTypes: Set<AccountType> = splits.mapNotNull { it.account?.type }.toSet(),
        ): Boolean =
            splits.size == 2 &&
                accountTypes == setOf(AccountType.ASSET, AccountType.EQUITY)

        fun isOpeningDebtEntry(
            splits: List<LedgerSplit>,
            accountTypes: Set<AccountType> = splits.mapNotNull { it.account?.type }.toSet(),
        ): Boolean =
            splits.size == 2 &&
                accountTypes == setOf(AccountType.LIABILITY, AccountType.EQUITY)

        fun isDebtPayment(splits: List<LedgerSplit>): Boolean {
            val accountTypes = splits.mapNotNull { it.account?.type }.toSet()
            val hasLiabilityDebit =
                splits.any {
                    it.account?.type == AccountType.LIABILITY && it.side == SplitSide.DEBIT
                }
            val hasAssetCredit =
                splits.any {
                    it.account?.type == AccountType.ASSET && it.side == SplitSide.CREDIT
                }
            return accountTypes.all { it == AccountType.ASSET || it == AccountType.LIABILITY } &&
                hasLiabilityDebit &&
                hasAssetCredit
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
            )
        }
    }
}
