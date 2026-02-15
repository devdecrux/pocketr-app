package com.decrux.pocketr_api.entities.db.household

import com.decrux.pocketr_api.entities.db.auth.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "household_member",
    indexes = [Index(name = "idx_member_user", columnList = "user_id")],
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
