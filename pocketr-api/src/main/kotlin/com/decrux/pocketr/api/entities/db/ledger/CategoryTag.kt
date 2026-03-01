package com.decrux.pocketr.api.entities.db.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
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
