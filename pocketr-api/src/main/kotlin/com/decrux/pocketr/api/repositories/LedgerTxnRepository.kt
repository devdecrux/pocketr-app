package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface LedgerTxnRepository :
    JpaRepository<LedgerTxn, UUID>,
    JpaSpecificationExecutor<LedgerTxn> {
    @EntityGraph(attributePaths = ["splits", "splits.account", "splits.categoryTag", "createdBy"])
    override fun findAll(spec: Specification<LedgerTxn>): List<LedgerTxn>

    @EntityGraph(attributePaths = ["splits", "splits.account", "splits.categoryTag", "createdBy"])
    override fun findAll(
        spec: Specification<LedgerTxn>,
        pageable: Pageable,
    ): Page<LedgerTxn>

    // Keep split accounts lazy so deletion initializes them through the ordered locking query.
    @EntityGraph(attributePaths = ["splits", "createdBy"])
    fun findOneById(id: UUID): Optional<LedgerTxn>

    // Row lock without the splits fetch join (FOR UPDATE cannot apply to the nullable side of an outer join).
    // Splits and accounts load lazily inside the caller's transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAndLockById(id: UUID): Optional<LedgerTxn>
}
