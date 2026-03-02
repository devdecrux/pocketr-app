package com.decrux.pocketr.api.entities.db.household;

import com.decrux.pocketr.api.entities.db.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "household_member",
    indexes = {
        @Index(name = "idx_member_household", columnList = "household_id"),
        @Index(name = "idx_member_household_status", columnList = "household_id, status"),
        @Index(name = "idx_member_user_status", columnList = "user_id, status")
    }
)
@IdClass(HouseholdMemberId.class)
public class HouseholdMember {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HouseholdRole role = HouseholdRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;

    public HouseholdMember() {
    }

    public HouseholdMember(
        Household household,
        User user,
        HouseholdRole role,
        MemberStatus status,
        User invitedBy,
        Instant invitedAt,
        Instant joinedAt
    ) {
        this.household = household;
        this.user = user;
        this.role = role;
        this.status = status;
        this.invitedBy = invitedBy;
        this.invitedAt = invitedAt;
        this.joinedAt = joinedAt;
    }

    public Household getHousehold() {
        return household;
    }

    public void setHousehold(Household household) {
        this.household = household;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public HouseholdRole getRole() {
        return role;
    }

    public void setRole(HouseholdRole role) {
        this.role = role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(Instant invitedAt) {
        this.invitedAt = invitedAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HouseholdMember that)) {
            return false;
        }
        return Objects.equals(getHouseholdId(), that.getHouseholdId()) && Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHouseholdId(), getUserId());
    }

    private UUID getHouseholdId() {
        return household != null ? household.getId() : null;
    }

    private Long getUserId() {
        return user != null ? user.getUserId() : null;
    }
}
