package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class HouseholdIdPresenceValidator {
    fun validate(householdId: UUID?): UUID =
        householdId ?: throw BadRequestException("householdId is required for household mode")
}
