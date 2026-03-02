package com.decrux.pocketr.api.services.ledger.validations;

import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TransactionAccountCurrencyValidator {

    public void validate(List<Account> accounts, String transactionCurrency) {
        for (Account account : accounts) {
            String accountCurrency = account.getCurrency() != null ? account.getCurrency().getCode() : null;
            if (!Objects.equals(accountCurrency, transactionCurrency)) {
                throw new BadRequestException(
                    "Account '"
                        + account.getName()
                        + "' has currency "
                        + accountCurrency
                        + " but transaction currency is "
                        + transactionCurrency
                );
            }
        }
    }
}
