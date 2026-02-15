package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.ledger.CategoryTag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CategoryTagRepository : JpaRepository<CategoryTag, UUID> {

    fun findByOwnerUserId(userId: Long): List<CategoryTag>

    fun existsByOwnerUserIdAndNameIgnoreCase(userId: Long, name: String): Boolean
}
