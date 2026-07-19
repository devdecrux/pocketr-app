package com.decrux.pocketr.api.services.account

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.exceptions.NotFoundException
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.services.OwnershipGuard
import com.decrux.pocketr.api.services.ledger.ManageLedger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class OpeningBalanceServiceImpl(
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val manageLedger: ManageLedger,
    private val ownershipGuard: OwnershipGuard,
) : OpeningBalanceService {
    @Transactional
    override fun createForNewAccount(
        owner: User,
        account: Account,
        openingBalanceMinor: Long,
        txnDate: LocalDate,
    ) {
        val ownerId = requireNotNull(owner.userId) { "User ID must not be null" }
        val accountId = requireNotNull(account.id) { "Account ID must not be null" }
        val currency = requireNotNull(account.currency) { "Currency must not be null" }
        val currencyCode = currency.code

        validateOpeningBalanceRequest(
            ownerId = ownerId,
            account = account,
            openingBalanceMinor = openingBalanceMinor,
        )

        val openingEquity = getOrCreateOpeningEquityAccount(owner, currencyCode, currency)
        val openingEquityId = requireNotNull(openingEquity.id) { "Opening equity account ID must not be null" }
        val absoluteAmount = toPositiveSplitAmount(openingBalanceMinor)
        val (accountSide, equitySide) = resolveOpeningBalanceSides(account.type, openingBalanceMinor)
        val descriptionPrefix = if (account.type == AccountType.LIABILITY) "Opening debt" else "Opening balance"

        manageLedger.createTransaction(
            dto =
                CreateTransactionDto(
                    mode = "INDIVIDUAL",
                    householdId = null,
                    txnDate = txnDate,
                    currency = currencyCode,
                    description = "$descriptionPrefix - ${account.name}",
                    splits =
                        listOf(
                            CreateSplitDto(
                                accountId = accountId,
                                side = accountSide,
                                amountMinor = absoluteAmount,
                            ),
                            CreateSplitDto(
                                accountId = openingEquityId,
                                side = equitySide,
                                amountMinor = absoluteAmount,
                            ),
                        ),
                ),
            creator = owner,
        )
    }

    /**
     * Validates that the requested account and signed opening balance can produce a ledger entry.
     */
    private fun validateOpeningBalanceRequest(
        ownerId: Long,
        account: Account,
        openingBalanceMinor: Long,
    ) {
        if (account.type !in SUPPORTED_OPENING_BALANCE_TYPES) {
            throw BadRequestException("Opening balance is supported only for ASSET and LIABILITY accounts")
        }

        ownershipGuard.requireOwner(account.owner?.userId, ownerId, "Not the owner of this account")

        if (openingBalanceMinor == 0L) {
            throw BadRequestException("openingBalanceMinor must not be zero")
        }

        if (openingBalanceMinor == Long.MIN_VALUE) {
            throw BadRequestException("openingBalanceMinor is out of supported range")
        }

        if (account.type == AccountType.LIABILITY && openingBalanceMinor < 0L) {
            throw BadRequestException("Opening debt must be positive for LIABILITY accounts")
        }
    }

    /**
     * Converts a signed opening balance into the positive amount stored on ledger splits.
     *
     * Ledger split amounts are always positive; the original balance direction is represented by
     * the debit/credit split side chosen by the caller.
     */
    private fun toPositiveSplitAmount(openingBalanceMinor: Long): Long =
        if (openingBalanceMinor > 0) openingBalanceMinor else -openingBalanceMinor

    /**
     * Maps a signed opening balance to the ledger sides used for the account and Opening Equity.
     *
     * Positive asset balances increase the asset account with a debit, negative asset balances
     * credit the asset account, and liability opening debts are recorded as credits.
     */
    private fun resolveOpeningBalanceSides(
        accountType: AccountType,
        openingBalanceMinor: Long,
    ): Pair<String, String> =
        when (accountType) {
            AccountType.ASSET -> {
                if (openingBalanceMinor > 0) {
                    "DEBIT" to "CREDIT"
                } else {
                    "CREDIT" to "DEBIT"
                }
            }

            AccountType.LIABILITY -> {
                "CREDIT" to "DEBIT"
            }

            else -> {
                throw BadRequestException("Unsupported account type for opening balance")
            }
        }

    /**
     * Returns the owner's system-managed Opening Equity account for the requested currency.
     *
     * The user row is locked before lookup/creation so concurrent opening-balance requests for the
     * same owner do not create duplicate Opening Equity accounts.
     */
    private fun getOrCreateOpeningEquityAccount(
        owner: User,
        currencyCode: String,
        currency: Currency,
    ): Account {
        val ownerId = requireNotNull(owner.userId) { "User ID must not be null" }

        userRepository
            .findByUserIdForUpdate(ownerId)
            .orElseThrow { NotFoundException("User not found") }

        accountRepository
            .findByOwnerUserIdAndTypeAndCurrencyCodeAndNameAndStatus(
                userId = ownerId,
                type = AccountType.EQUITY,
                currencyCode = currencyCode,
                name = OPENING_EQUITY_NAME,
                status = AccountStatus.ACTIVE,
            )?.let { return it }

        return accountRepository.save(
            Account(
                owner = owner,
                name = OPENING_EQUITY_NAME,
                type = AccountType.EQUITY,
                currency = currency,
            ),
        )
    }

    private companion object {
        const val OPENING_EQUITY_NAME = "Opening Equity"
        val SUPPORTED_OPENING_BALANCE_TYPES = setOf(AccountType.ASSET, AccountType.LIABILITY)
    }
}
