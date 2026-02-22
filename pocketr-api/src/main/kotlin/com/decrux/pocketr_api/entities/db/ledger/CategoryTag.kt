package com.decrux.pocketr_api.entities.db.ledger

import com.decrux.pocketr_api.entities.db.auth.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "category_tag",
    uniqueConstraints = [UniqueConstraint(columnNames = ["owner_user_id", "name"])],
)
class CategoryTag(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    var owner: User? = null,
    @Column(nullable = false)
    var name: String = "",
    @Column(length = 7)
    var color: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
)
