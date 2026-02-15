package com.decrux.pocketr_api.entities.db.household

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.Account
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "household_account_share")
@IdClass(HouseholdAccountShareId::class)
class HouseholdAccountShare(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    var household: Household? = null,
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by", nullable = false)
    var sharedBy: User? = null,
    @Column(name = "shared_at", nullable = false, updatable = false)
    var sharedAt: Instant = Instant.now(),
)
