package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class CrossUserAssetAccountTypeValidator {
    fun validate(accounts: List<Account>) {
        accounts.forEach { account ->
            if (account.type != AccountType.ASSET) {
                throw BadRequestException(
                    "Cross-user transfers only allow ASSET accounts (v1), but '${account.name}' is ${account.type}",
                )
            }
        }
    }
}
