package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DoubleEntryBalanceValidator {

    public void validate(List<CreateSplitDto> splits) {
        long sumDebits = 0L;
        long sumCredits = 0L;

        for (CreateSplitDto split : splits) {
            if ("DEBIT".equals(split.getSide())) {
                sumDebits += split.getAmountMinor();
            }
            if ("CREDIT".equals(split.getSide())) {
                sumCredits += split.getAmountMinor();
            }
        }

        if (sumDebits != sumCredits) {
            throw new BadRequestException(
                "Double-entry violation: sum of debits ("
                    + sumDebits
                    + ") must equal sum of credits ("
                    + sumCredits
                    + ")"
            );
        }
    }
}
