package com.decrux.pocketr.api.entities.db.settings

import com.decrux.pocketr.api.entities.db.ledger.Currency
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "app_settings",
    check = [
        CheckConstraint(
            name = "ck_app_settings_singleton",
            constraint = "id = 1",
        ),
    ],
)
class AppSettings(
    @Id
    var id: Short = SINGLETON_ID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "base_currency",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_app_settings_base_currency"),
    )
    var baseCurrency: Currency? = null,
) {
    companion object {
        const val SINGLETON_ID: Short = 1
    }
}
