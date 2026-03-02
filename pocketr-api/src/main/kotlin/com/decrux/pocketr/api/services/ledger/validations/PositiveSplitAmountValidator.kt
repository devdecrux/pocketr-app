package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class PositiveSplitAmountValidator {
    fun validate(splits: List<CreateSplitDto>) {
        splits.forEach { split ->
            if (split.amountMinor <= 0) {
                throw BadRequestException("All split amounts must be greater than 0")
            }
        }
    }
}
