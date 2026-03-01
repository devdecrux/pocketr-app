package com.decrux.pocketr.api.services.account

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import java.time.LocalDate

interface OpeningBalanceService {
    fun createForNewAssetAccount(
        owner: User,
        assetAccount: Account,
        openingBalanceMinor: Long,
        txnDate: LocalDate,
    )
}
