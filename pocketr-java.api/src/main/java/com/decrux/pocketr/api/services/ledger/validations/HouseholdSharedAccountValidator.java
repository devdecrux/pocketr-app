package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HouseholdSharedAccountValidator {

    public void validate(List<Account> nonOwnedAccounts, ManageHousehold manageHousehold, UUID householdId) {
        for (Account account : nonOwnedAccounts) {
            UUID accountId = requireNotNull(account.getId());
            if (!manageHousehold.isAccountShared(householdId, accountId)) {
                throw new ForbiddenException("Account '" + account.getName() + "' is not shared into household");
            }
        }
    }

    private static <T> T requireNotNull(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Required value was null.");
        }
        return value;
    }
}
