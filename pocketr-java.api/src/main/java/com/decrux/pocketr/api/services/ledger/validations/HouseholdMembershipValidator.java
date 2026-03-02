package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HouseholdMembershipValidator {

    public void validate(ManageHousehold manageHousehold, UUID householdId, long userId) {
        if (!manageHousehold.isActiveMember(householdId, userId)) {
            throw new ForbiddenException("Not an active member of this household");
        }
    }
}
