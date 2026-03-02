package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.ledger.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {
}
