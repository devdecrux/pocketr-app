package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.PocketrUser
import com.decrux.pocketr_api.entities.dtos.PocketrUserDto
import com.decrux.pocketr_api.entities.dtos.RegisterUserDto
import com.decrux.pocketr_api.services.user_registration.RegisterUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/user")
class UsersController(
    private val registerUser: RegisterUser,
) {

    @PostMapping("/register")
    fun registerUser(@RequestBody registerUserDto: RegisterUserDto) {
        registerUser.registerUser(registerUserDto)
    }

    @GetMapping
    fun retrieveUserData(@AuthenticationPrincipal user: PocketrUser): PocketrUserDto {
        return PocketrUserDto(
            id = user.userId,
            email = user.email,
            username = user.username,
            firstName = user.firstName,
            lastName = user.lastName,
        )
    }
}
