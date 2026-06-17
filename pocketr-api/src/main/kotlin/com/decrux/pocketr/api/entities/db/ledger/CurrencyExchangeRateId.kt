package com.decrux.pocketr.api.entities.db.ledger

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class CurrencyExchangeRateId(
    @Column(name = "base_currency", length = 3)
    var baseCurrency: String = "",
    @Column(name = "quote_currency", length = 3)
    var quoteCurrency: String = "",
) : Serializable
