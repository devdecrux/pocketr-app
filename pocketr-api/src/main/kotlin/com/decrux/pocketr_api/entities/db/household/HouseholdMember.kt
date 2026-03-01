package com.decrux.pocketr_api.entities.db.household

import com.decrux.pocketr_api.entities.db.auth.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "household_member",
    indexes = [
        Index(name = "idx_member_household", columnList = "household_id"),
        Index(name = "idx_member_household_status", columnList = "household_id, status"),
        Index(name = "idx_member_user_status", columnList = "user_id, status"),
    ],
)
@IdClass(HouseholdMemberId::class)
class HouseholdMember(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    var household: Household? = null,
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: HouseholdRole = HouseholdRole.MEMBER,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MemberStatus = MemberStatus.ACTIVE,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    var invitedBy: User? = null,
    @Column(name = "invited_at")
    var invitedAt: Instant? = null,
    @Column(name = "joined_at")
    var joinedAt: Instant? = null,
)
