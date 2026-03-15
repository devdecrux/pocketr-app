package com.decrux.pocketr.api.services.ledger.validations

import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import org.springframework.stereotype.Component

@Component
class CrossUserAssetAccountTypeValidator {
    fun validate(
        accounts: List<Account>,
        splits: List<CreateSplitDto>,
        creatorUserId: Long,
    ) {
        if (accounts.all { it.type == AccountType.ASSET }) {
            return
        }

        if (isAllowedSharedLiabilityRepayment(accounts, splits, creatorUserId)) {
            return
        }

        throw BadRequestException(
            "Cross-user household postings only allow ASSET accounts, except repaying a shared LIABILITY from your own ASSET account",
        )
    }

    private fun isAllowedSharedLiabilityRepayment(
        accounts: List<Account>,
        splits: List<CreateSplitDto>,
        creatorUserId: Long,
    ): Boolean {
        val accountsById = accounts.associateBy { requireNotNull(it.id) }
        val includesAssetAccount = accounts.any { it.type == AccountType.ASSET }
        val includesLiabilityAccount = accounts.any { it.type == AccountType.LIABILITY }
        val everyNonOwnedAccountIsLiability =
            accounts
                .filter { it.owner?.userId != creatorUserId }
                .all { it.type == AccountType.LIABILITY }

        if (!includesAssetAccount || !includesLiabilityAccount || !everyNonOwnedAccountIsLiability) {
            return false
        }

        return splits.all { split ->
            val splitAccountType = accountsById.getValue(split.accountId).type
            when (splitAccountType) {
                AccountType.ASSET -> split.side == SplitSide.CREDIT.name
                AccountType.LIABILITY -> split.side == SplitSide.DEBIT.name
                else -> false
            }
        }
    }
}
