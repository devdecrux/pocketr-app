package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.exceptions.ForbiddenException
import com.decrux.pocketr.api.services.household.ManageHousehold
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class HouseholdSharedAccountValidator {
    fun validate(
        nonOwnedAccounts: List<Account>,
        manageHousehold: ManageHousehold,
        householdId: UUID,
    ) {
        nonOwnedAccounts.forEach { account ->
            val accountId = requireNotNull(account.id)
            if (!manageHousehold.isAccountShared(householdId, accountId)) {
                throw ForbiddenException(
                    "Account '${account.name}' is not shared into household",
                )
            }
        }
    }
}
