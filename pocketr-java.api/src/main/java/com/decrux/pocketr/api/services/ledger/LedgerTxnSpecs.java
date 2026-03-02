package com.decrux.pocketr.api.services.ledger;

import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class LedgerTxnSpecs {

    private LedgerTxnSpecs() {
    }

    public static Specification<LedgerTxn> forUser(long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("userId"), userId);
    }

    public static Specification<LedgerTxn> dateFrom(LocalDate date) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("txnDate"), date);
    }

    public static Specification<LedgerTxn> dateTo(LocalDate date) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("txnDate"), date);
    }

    public static Specification<LedgerTxn> hasAccount(UUID accountId) {
        return (root, query, cb) -> {
            var splits = root.join("splits");
            query.distinct(true);
            return cb.equal(splits.get("account").get("id"), accountId);
        };
    }

    public static Specification<LedgerTxn> hasCategory(UUID categoryId) {
        return (root, query, cb) -> {
            var splits = root.join("splits");
            query.distinct(true);
            return cb.equal(splits.get("categoryTag").get("id"), categoryId);
        };
    }

    public static Specification<LedgerTxn> hasAnySharedAccount(Set<UUID> accountIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            var splits = root.join("splits");
            return splits.get("account").get("id").in(accountIds);
        };
    }
}
