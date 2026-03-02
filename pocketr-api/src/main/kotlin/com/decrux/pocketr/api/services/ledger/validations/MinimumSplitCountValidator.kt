package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class MinimumSplitCountValidator {
    fun validate(splits: List<CreateSplitDto>) {
        if (splits.size < 2) {
            throw BadRequestException("Transaction must have at least 2 splits")
        }
    }
}
