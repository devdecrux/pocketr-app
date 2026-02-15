package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.ledger.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {

    fun findByOwnerUserId(userId: Long): List<Account>
}
