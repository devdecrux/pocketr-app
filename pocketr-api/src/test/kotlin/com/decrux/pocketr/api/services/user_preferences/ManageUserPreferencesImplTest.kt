package com.decrux.pocketr.api.services.user_preferences

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.dtos.UpdateRolloverDayDto
import com.decrux.pocketr.api.entities.dtos.UpdateUserLanguageDto
import com.decrux.pocketr.api.entities.dtos.UserDto
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class ManageUserPreferencesImplTest {
    private val userRepository = mock(UserRepository::class.java)
    private val userAvatarService = mock(UserAvatarService::class.java)
    private val service = ManageUserPreferencesImpl(userRepository, userAvatarService)

    @Test
    fun updatingRolloverDayAlsoUpdatesSessionPrincipal() {
        val principal = User(userId = 1L, email = "user@example.com", rolloverDay = 1)
        val persisted = User(userId = 1L, email = "user@example.com", rolloverDay = 1)
        val updatedDto = UserDto(1L, persisted.email, null, null, "en", 23, null)
        `when`(userRepository.findById(1L)).thenReturn(Optional.of(persisted))
        `when`(userRepository.save(persisted)).thenReturn(persisted)
        `when`(userAvatarService.toUserDto(persisted)).thenReturn(updatedDto)

        val result = service.updateRolloverDay(principal, UpdateRolloverDayDto(23))

        assertEquals(23, persisted.rolloverDay)
        assertEquals(23, principal.rolloverDay)
        assertEquals(23, result.rolloverDay)
    }

    @Test
    fun updatingLanguageAlsoUpdatesSessionPrincipal() {
        val principal = User(userId = 1L, email = "user@example.com", language = "en")
        val persisted = User(userId = 1L, email = "user@example.com", language = "en")
        val updatedDto = UserDto(1L, persisted.email, null, null, "bg", 1, null)
        `when`(userRepository.findById(1L)).thenReturn(Optional.of(persisted))
        `when`(userRepository.save(persisted)).thenReturn(persisted)
        `when`(userAvatarService.toUserDto(persisted)).thenReturn(updatedDto)

        val result = service.updateLanguage(principal, UpdateUserLanguageDto("bg"))

        assertEquals("bg", persisted.language)
        assertEquals("bg", principal.language)
        assertEquals("bg", result.language)
    }
}
