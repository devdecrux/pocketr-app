package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.AccountCurrentBalance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AccountCurrentBalanceRepository : JpaRepository<AccountCurrentBalance, UUID> {
    @Modifying
    @Query(
        value = """
            INSERT INTO account_current_balance(account_id, raw_balance_minor, updated_at)
            VALUES (:accountId, :delta, now())
            ON CONFLICT (account_id) DO UPDATE
            SET raw_balance_minor = account_current_balance.raw_balance_minor + EXCLUDED.raw_balance_minor,
                updated_at = now()
        """,
        nativeQuery = true,
    )
    fun addDelta(
        @Param("accountId") accountId: UUID,
        @Param("delta") delta: Long,
    ): Int

    fun findAllByAccountIdIn(accountIds: Collection<UUID>): List<AccountCurrentBalance>
}
