package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CrossUserAssetAccountTypeValidator {

    public void validate(List<Account> accounts) {
        for (Account account : accounts) {
            if (account.getType() != AccountType.ASSET) {
                throw new BadRequestException(
                    "Cross-user transfers only allow ASSET accounts (v1), but '"
                        + account.getName()
                        + "' is "
                        + account.getType()
                );
            }
        }
    }
}
