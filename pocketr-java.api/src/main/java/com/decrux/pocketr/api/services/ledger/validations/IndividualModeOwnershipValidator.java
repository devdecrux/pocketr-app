package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IndividualModeOwnershipValidator {

    public void validate(List<Account> nonOwnedAccounts, boolean isHouseholdMode) {
        if (!nonOwnedAccounts.isEmpty() && !isHouseholdMode) {
            throw new ForbiddenException("Cannot post to accounts not owned by current user in individual mode");
        }
    }
}
