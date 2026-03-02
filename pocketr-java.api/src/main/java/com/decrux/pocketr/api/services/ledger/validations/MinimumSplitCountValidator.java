package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MinimumSplitCountValidator {

    public void validate(List<CreateSplitDto> splits) {
        if (splits.size() < 2) {
            throw new BadRequestException("Transaction must have at least 2 splits");
        }
    }
}
