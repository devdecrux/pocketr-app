package com.decrux.pocketr_api.services.user_registration

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.auth.UserRole
import com.decrux.pocketr_api.entities.dtos.RegisterUserDto
import com.decrux.pocketr_api.repositories.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterUserImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : RegisterUser {
    @Transactional
    override fun registerUser(userDto: RegisterUserDto) {
        val email = userDto.email.trim()

        val user =
            User(
                password =
                    requireNotNull(passwordEncoder.encode(userDto.password.trim())) {
                        "Password encoder returned null"
                    },
                email = email,
                firstName = userDto.firstName,
                lastName = userDto.lastName,
                roles = mutableListOf(UserRole(role = ROLE_USER)),
            )
        userRepository.saveAndFlush(user)
    }

    private companion object {
        const val ROLE_USER = "USER"
    }
}
