package com.decrux.pocketr_api.entities.db.household

import com.decrux.pocketr_api.entities.db.auth.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "household")
class Household(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var name: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    var createdBy: User? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "household", cascade = [CascadeType.ALL], orphanRemoval = true)
    var members: MutableList<HouseholdMember> = mutableListOf(),
)
