package com.decrux.pocketr_api.services.account

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import java.time.LocalDate

interface OpeningBalanceService {
    fun createForNewAssetAccount(
        owner: User,
        assetAccount: Account,
        openingBalanceMinor: Long,
        txnDate: LocalDate,
    )
}
