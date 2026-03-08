package com.decrux.pocketr.api.entities.db.household

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "household_account_share")
@IdClass(HouseholdAccountShareId::class)
class HouseholdAccountShare(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false, foreignKey = ForeignKey(name = "fk_household_account_share_household"))
    var household: Household? = null,
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = ForeignKey(name = "fk_household_account_share_account"))
    var account: Account? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by", nullable = false, foreignKey = ForeignKey(name = "fk_household_account_share_shared_by"))
    var sharedBy: User? = null,
    @Column(name = "shared_at", nullable = false, updatable = false)
    var sharedAt: Instant = Instant.now(),
)
