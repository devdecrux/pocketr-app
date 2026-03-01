package com.decrux.pocketr.api.entities.db.auth

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var userId: Long? = null,
    @Column(nullable = false)
    private var password: String = "",
    @Column(nullable = false, unique = true)
    var email: String = "",
    var firstName: String? = null,
    var lastName: String? = null,
    @Column(name = "avatar_path")
    var avatarPath: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var roles: MutableList<UserRole> = mutableListOf(),
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        roles
            .toSet()
            .map { SimpleGrantedAuthority("ROLE_${it.role}") }
            .toSet()

    override fun getPassword(): String = password

    override fun getUsername(): String = email
}
