package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class SplitSideValueValidator {
    fun validate(splits: List<CreateSplitDto>) {
        splits.forEach { split ->
            try {
                SplitSide.valueOf(split.side)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("Invalid split side: ${split.side}")
            }
        }
    }
}
