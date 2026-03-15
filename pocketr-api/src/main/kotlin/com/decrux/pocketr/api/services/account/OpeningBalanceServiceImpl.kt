package com.decrux.pocketr.api.services.account

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
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

        val openingEquity = getOrCreateOpeningEquityAccount(owner, currencyCode, currency)
        val openingEquityId = requireNotNull(openingEquity.id) { "Opening equity account ID must not be null" }
        val absoluteAmount = if (openingBalanceMinor > 0) openingBalanceMinor else -openingBalanceMinor
        val (accountSide, equitySide) =
            when (account.type) {
                AccountType.ASSET -> {
                    if (openingBalanceMinor > 0) {
                        "DEBIT" to "CREDIT"
                    } else {
                        "CREDIT" to "DEBIT"
                    }
                }

                AccountType.LIABILITY -> "CREDIT" to "DEBIT"
                else -> throw BadRequestException("Unsupported account type for opening balance")
            }
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

    private fun getOrCreateOpeningEquityAccount(
        owner: User,
        currencyCode: String,
        currency: Currency,
    ): Account {
        val ownerId = requireNotNull(owner.userId) { "User ID must not be null" }

        // Serialize Opening Equity creation per user without introducing broad account-name constraints.
        userRepository
            .findByUserIdForUpdate(ownerId)
            .orElseThrow { NotFoundException("User not found") }

        accountRepository
            .findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
                userId = ownerId,
                type = AccountType.EQUITY,
                currencyCode = currencyCode,
                name = OPENING_EQUITY_NAME,
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
