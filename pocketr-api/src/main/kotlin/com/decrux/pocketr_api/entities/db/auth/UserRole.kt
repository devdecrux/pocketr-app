package com.decrux.pocketr_api.entities.db.auth

import jakarta.persistence.*

@Entity
@Table(name = "user_roles")
class UserRole(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var role: String = "",
)
