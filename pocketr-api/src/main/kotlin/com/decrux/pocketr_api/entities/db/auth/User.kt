package com.decrux.pocketr_api.entities.db.auth

import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var userId: Long? = null,
    @Column(name = "password", nullable = false)
    var passwordValue: String = "",
    @Column(nullable = false, unique = true)
    var email: String = "",
    var firstName: String? = null,
    var lastName: String? = null,
    @Column(name = "avatar_path")
    var avatarPath: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    var roles: MutableList<UserRole> = mutableListOf(),
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return roles
            .toSet()
            .map { SimpleGrantedAuthority("ROLE_${it.role}") }
            .toSet()
    }

    override fun getPassword(): String = passwordValue

    override fun getUsername(): String = email
}
