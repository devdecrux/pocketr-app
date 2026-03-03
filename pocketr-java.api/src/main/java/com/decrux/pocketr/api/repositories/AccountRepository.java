package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    @EntityGraph(attributePaths = {"currency"})
    List<Account> findByOwnerUserId(long userId);

    @EntityGraph(attributePaths = {"currency"})
    Account findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
        long userId,
        AccountType type,
        String currencyCode,
        String name
    );

    default Optional<Account> findOptionalByOwnerUserIdAndTypeAndCurrencyCodeAndName(
        long userId,
        AccountType type,
        String currencyCode,
        String name
    ) {
        return Optional.ofNullable(findByOwnerUserIdAndTypeAndCurrencyCodeAndName(userId, type, currencyCode, name));
    }
}
