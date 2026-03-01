package com.decrux.pocketr.api.config.security

import com.decrux.pocketr.api.exceptions.UserNotFoundException
import com.decrux.pocketr.api.repositories.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    @Transactional
    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(email: String): UserDetails =
        userRepository.findUserByEmail(email)
            ?: throw UserNotFoundException("User with email $email not found")
}
