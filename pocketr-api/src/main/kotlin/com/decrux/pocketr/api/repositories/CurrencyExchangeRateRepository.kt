package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRate
import com.decrux.pocketr.api.entities.db.ledger.CurrencyExchangeRateId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CurrencyExchangeRateRepository : JpaRepository<CurrencyExchangeRate, CurrencyExchangeRateId> {
    fun findAllByIdBaseCurrency(baseCurrency: String): List<CurrencyExchangeRate>

    fun deleteByIdBaseCurrency(baseCurrency: String)
}
