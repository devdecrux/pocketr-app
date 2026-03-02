package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HouseholdIdPresenceValidator {

    public UUID validate(UUID householdId) {
        if (householdId == null) {
            throw new BadRequestException("householdId is required for household mode");
        }
        return householdId;
    }
}
