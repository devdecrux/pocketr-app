package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.ledger.Currency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CurrencyRepository : JpaRepository<Currency, String>
