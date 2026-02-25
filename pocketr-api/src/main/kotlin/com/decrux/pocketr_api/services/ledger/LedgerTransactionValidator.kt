package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.SplitSide
import com.decrux.pocketr_api.entities.dtos.CreateSplitDto
import com.decrux.pocketr_api.exceptions.DomainBadRequestException
import org.springframework.stereotype.Component

@Component
class LedgerTransactionValidator {

    fun validateSplits(splits: List<CreateSplitDto>) {
        if (splits.size < 2) {
            throw DomainBadRequestException("Transaction must have at least 2 splits")
        }

        splits.forEach { split ->
            if (split.amountMinor <= 0) {
                throw DomainBadRequestException("All split amounts must be greater than 0")
            }
        }

        splits.forEach { split ->
            try {
                SplitSide.valueOf(split.side)
            } catch (_: IllegalArgumentException) {
                throw DomainBadRequestException("Invalid split side: ${split.side}")
            }
        }

        val sumDebits = splits.filter { it.side == "DEBIT" }.sumOf { it.amountMinor }
        val sumCredits = splits.filter { it.side == "CREDIT" }.sumOf { it.amountMinor }
        if (sumDebits != sumCredits) {
            throw DomainBadRequestException(
                "Double-entry violation: sum of debits ($sumDebits) must equal sum of credits ($sumCredits)",
            )
        }
    }

    fun validateCurrencyConsistency(accounts: List<Account>, transactionCurrency: String) {
        accounts.forEach { account ->
            if (account.currency?.code != transactionCurrency) {
                throw DomainBadRequestException(
                    "Account '${account.name}' has currency ${account.currency?.code} but transaction currency is $transactionCurrency",
                )
            }
        }
    }
}
