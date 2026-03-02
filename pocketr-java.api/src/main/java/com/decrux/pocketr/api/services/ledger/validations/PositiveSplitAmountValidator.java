package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PositiveSplitAmountValidator {

    public void validate(List<CreateSplitDto> splits) {
        for (CreateSplitDto split : splits) {
            if (split.getAmountMinor() <= 0) {
                throw new BadRequestException("All split amounts must be greater than 0");
            }
        }
    }
}
