package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findOneById(id: UUID): Optional<Account>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByIdInOrderByIdAsc(ids: Collection<UUID>): List<Account>

    fun findByOwnerUserId(userId: Long): List<Account>

    fun findByOwnerUserIdAndStatus(
        userId: Long,
        status: AccountStatus,
    ): List<Account>

    fun findByOwnerUserIdAndTypeAndCurrencyCodeAndNameAndStatus(
        userId: Long,
        type: AccountType,
        currencyCode: String,
        name: String,
        status: AccountStatus,
    ): Account?
}
