package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.RegisterUserDto
import com.decrux.pocketr_api.entities.dtos.UserDto
import com.decrux.pocketr_api.services.user_avatar.UserAvatarService
import com.decrux.pocketr_api.services.user_registration.RegisterUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1/user")
class UsersController(
    private val registerUser: RegisterUser,
    private val userAvatarService: UserAvatarService,
) {
    @GetMapping
    fun retrieveUserData(
        @AuthenticationPrincipal user: User,
    ): UserDto = userAvatarService.toUserDto(user)

    @PostMapping("/register")
    fun registerUser(
        @RequestBody registerUserDto: RegisterUserDto,
    ) {
        registerUser.registerUser(registerUserDto)
    }

    @PostMapping("/avatar")
    fun uploadAvatar(
        @RequestParam("avatar") avatar: MultipartFile,
        @AuthenticationPrincipal user: User,
    ): UserDto = userAvatarService.uploadAvatar(user, avatar)
}
