package com.decrux.pocketr.api.entities.db.ledger

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "currency")
class Currency(
    @Id
    @Column(length = 3)
    var code: String = "",
    @Column(name = "minor_unit", nullable = false)
    var minorUnit: Short = 0,
    @Column(nullable = false)
    var name: String = "",
)
