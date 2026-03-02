package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.exceptions.ForbiddenException
import com.decrux.pocketr.api.services.household.ManageHousehold
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class HouseholdMembershipValidator {
    fun validate(
        manageHousehold: ManageHousehold,
        householdId: UUID,
        userId: Long,
    ) {
        if (!manageHousehold.isActiveMember(householdId, userId)) {
            throw ForbiddenException("Not an active member of this household")
        }
    }
}
