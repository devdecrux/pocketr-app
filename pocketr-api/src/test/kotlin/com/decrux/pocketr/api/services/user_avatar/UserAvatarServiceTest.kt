package com.decrux.pocketr.api.services.user_avatar

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.services.storage.StorageInitializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Path
import java.util.Optional

class UserAvatarServiceTest {
    @TempDir
    lateinit var storageDir: Path

    private val userRepository = mock(UserRepository::class.java)

    @Test
    fun uploadingAvatarAlsoUpdatesSessionPrincipal() {
        val service = UserAvatarService(storageDir.toString(), StorageInitializer(), userRepository)
        val principal = User(userId = 1L, email = "user@example.com")
        val persisted = User(userId = 1L, email = "user@example.com")
        `when`(userRepository.findById(1L)).thenReturn(Optional.of(persisted))
        `when`(userRepository.save(persisted)).thenReturn(persisted)
        val avatar = MockMultipartFile("avatar", "avatar.png", "image/png", byteArrayOf(1, 2, 3))

        val result = service.uploadAvatar(principal, avatar)

        assertNotNull(persisted.avatarPath)
        assertEquals(persisted.avatarPath, principal.avatarPath)
        assertNotNull(result.avatar)
        assertEquals(result.avatar, service.toUserDto(principal).avatar)
    }
}
