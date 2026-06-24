package com.decrux.pocketr.api.entities.db.ledger

import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "currencies_exchange_rates",
    check = [
        CheckConstraint(
            name = "currencies_exchange_rates_rate_check",
            constraint = "rate > 0",
        ),
    ],
)
class CurrencyExchangeRate(
    @EmbeddedId
    var id: CurrencyExchangeRateId = CurrencyExchangeRateId(),
    @MapsId("baseCurrency")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_currency", nullable = false, foreignKey = ForeignKey(name = "fk_currency_rate_base"))
    var base: Currency? = null,
    @MapsId("quoteCurrency")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_currency", nullable = false, foreignKey = ForeignKey(name = "fk_currency_rate_quote"))
    var quote: Currency? = null,
    @Column(nullable = false, precision = 38, scale = 18)
    var rate: BigDecimal = BigDecimal.ONE,
    @Column(name = "provider_date", nullable = false)
    var providerDate: LocalDate = LocalDate.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
