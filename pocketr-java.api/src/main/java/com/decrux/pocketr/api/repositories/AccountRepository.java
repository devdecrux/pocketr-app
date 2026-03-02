package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerUserId(long userId);

    Account findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
        long userId,
        AccountType type,
        String currencyCode,
        String name
    );
}
