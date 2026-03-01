package com.decrux.pocketr_api.services.ledger

import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.exceptions.BadRequestException
import com.decrux.pocketr_api.exceptions.ForbiddenException
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LedgerTransactionPolicy(
    private val manageHousehold: ManageHousehold,
) {

    fun checkAccountAccess(
        accounts: List<Account>,
        userId: Long,
        isHouseholdMode: Boolean,
        householdId: UUID?,
    ) {
        val nonOwnedAccounts = accounts.filter { it.owner?.userId != userId }

        if (nonOwnedAccounts.isEmpty()) return

        if (!isHouseholdMode) {
            throw ForbiddenException(
                "Cannot post to accounts not owned by current user in individual mode",
            )
        }

        val hhId = householdId
            ?: throw BadRequestException("householdId is required for household mode")

        if (!manageHousehold.isActiveMember(hhId, userId)) {
            throw ForbiddenException("Not an active member of this household")
        }

        nonOwnedAccounts.forEach { account ->
            val accountId = requireNotNull(account.id)
            if (!manageHousehold.isAccountShared(hhId, accountId)) {
                throw ForbiddenException(
                    "Account '${account.name}' is not shared into household",
                )
            }
        }

        accounts.forEach { account ->
            if (account.type != AccountType.ASSET) {
                throw BadRequestException(
                    "Cross-user transfers only allow ASSET accounts (v1), but '${account.name}' is ${account.type}",
                )
            }
        }
    }
}
