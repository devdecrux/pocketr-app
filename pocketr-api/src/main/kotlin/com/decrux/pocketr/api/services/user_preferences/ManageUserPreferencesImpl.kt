package com.decrux.pocketr.api.services.user_preferences

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.dtos.UpdateUserLanguageDto
import com.decrux.pocketr.api.entities.dtos.UserDto
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ManageUserPreferencesImpl(
    private val userRepository: UserRepository,
    private val userAvatarService: UserAvatarService,
) : ManageUserPreferences {
    @Transactional
    override fun updateLanguage(
        user: User,
        dto: UpdateUserLanguageDto,
    ): UserDto {
        val language = dto.language.trim().lowercase()
        val userId = user.userId ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user id is missing")
        val persistedUser =
            userRepository
                .findById(userId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        persistedUser.language = language
        return userAvatarService.toUserDto(userRepository.save(persistedUser))
    }
}
