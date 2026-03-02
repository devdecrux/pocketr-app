package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerTxnRepository extends JpaRepository<LedgerTxn, UUID>, JpaSpecificationExecutor<LedgerTxn> {

    @EntityGraph(attributePaths = {"splits", "splits.account", "splits.categoryTag", "createdBy"})
    @Override
    List<LedgerTxn> findAll(Specification<LedgerTxn> spec);

    @EntityGraph(attributePaths = {"splits", "splits.account", "splits.categoryTag", "createdBy"})
    @Override
    Page<LedgerTxn> findAll(Specification<LedgerTxn> spec, Pageable pageable);
}
