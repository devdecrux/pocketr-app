package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.UserDto
import com.decrux.pocketr_api.entities.dtos.RegisterUserDto
import com.decrux.pocketr_api.repositories.UserRepository
import com.decrux.pocketr_api.services.user_avatar.UserAvatarService
import com.decrux.pocketr_api.services.user_registration.RegisterUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/v1/user")
class UsersController(
    private val registerUser: RegisterUser,
    private val userRepository: UserRepository,
    private val userAvatarService: UserAvatarService,
) {

    @PostMapping("/register")
    fun registerUser(@RequestBody registerUserDto: RegisterUserDto) {
        registerUser.registerUser(registerUserDto)
    }

    @GetMapping
    fun retrieveUserData(@AuthenticationPrincipal user: User): UserDto {
        return user.toDto()
    }

    @PostMapping("/avatar")
    fun uploadAvatar(
        @RequestParam("avatar") avatar: MultipartFile,
        @AuthenticationPrincipal user: User,
    ): UserDto {
        val userId = user.userId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user id is missing")

        val persistedUser = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val storedPath = try {
            userAvatarService.storeAvatar(persistedUser, avatar)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                e.message ?: "Invalid avatar upload request",
                e,
            )
        }

        persistedUser.avatarPath = storedPath
        val savedUser = userRepository.save(persistedUser)

        return savedUser.toDto()
    }

    private fun User.toDto(): UserDto {
        return UserDto(
            id = userId,
            email = email,
            username = username,
            firstName = firstName,
            lastName = lastName,
            avatar = userAvatarService.resolveAvatarDataUrl(avatarPath),
        )
    }
}
