package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class DoubleEntryBalanceValidator {
    fun validate(splits: List<CreateSplitDto>) {
        val sumDebits = splits.filter { it.side == "DEBIT" }.sumOf { it.amountMinor }
        val sumCredits = splits.filter { it.side == "CREDIT" }.sumOf { it.amountMinor }

        if (sumDebits != sumCredits) {
            throw BadRequestException(
                "Double-entry violation: sum of debits ($sumDebits) must equal sum of credits ($sumCredits)",
            )
        }
    }
}
