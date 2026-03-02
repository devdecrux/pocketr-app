package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SplitSideValueValidator {

    public void validate(List<CreateSplitDto> splits) {
        for (CreateSplitDto split : splits) {
            try {
                SplitSide.valueOf(split.getSide());
            } catch (IllegalArgumentException ignored) {
                throw new BadRequestException("Invalid split side: " + split.getSide());
            }
        }
    }
}
