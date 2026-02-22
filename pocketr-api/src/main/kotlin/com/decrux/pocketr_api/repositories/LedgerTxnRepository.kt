package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.ledger.LedgerTxn
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LedgerTxnRepository : JpaRepository<LedgerTxn, UUID>, JpaSpecificationExecutor<LedgerTxn> {

    @EntityGraph(attributePaths = ["splits", "splits.account", "splits.categoryTag", "createdBy"])
    override fun findAll(spec: Specification<LedgerTxn>): List<LedgerTxn>

    @EntityGraph(attributePaths = ["splits", "splits.account", "splits.categoryTag", "createdBy"])
    override fun findAll(spec: Specification<LedgerTxn>, pageable: Pageable): Page<LedgerTxn>
}
