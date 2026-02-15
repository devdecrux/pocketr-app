package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.ledger.Account
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {

    fun findByOwnerUserId(userId: Long): List<Account>

    fun findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
        userId: Long,
        type: AccountType,
        currencyCode: String,
        name: String,
    ): Account?
}
