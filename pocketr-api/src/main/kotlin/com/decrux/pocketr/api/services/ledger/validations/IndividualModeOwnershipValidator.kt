package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.exceptions.ForbiddenException
import org.springframework.stereotype.Component

@Component
class IndividualModeOwnershipValidator {
    fun validate(
        nonOwnedAccounts: List<Account>,
        isHouseholdMode: Boolean,
    ) {
        if (nonOwnedAccounts.isNotEmpty() && !isHouseholdMode) {
            throw ForbiddenException(
                "Cannot post to accounts not owned by current user in individual mode",
            )
        }
    }
}
