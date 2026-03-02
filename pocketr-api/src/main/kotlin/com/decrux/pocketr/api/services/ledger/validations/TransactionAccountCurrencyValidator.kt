package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class TransactionAccountCurrencyValidator {
    fun validate(
        accounts: List<Account>,
        transactionCurrency: String,
    ) {
        accounts.forEach { account ->
            if (account.currency?.code != transactionCurrency) {
                throw BadRequestException(
                    "Account '${account.name}' has currency ${account.currency?.code} but transaction currency is $transactionCurrency",
                )
            }
        }
    }
}
