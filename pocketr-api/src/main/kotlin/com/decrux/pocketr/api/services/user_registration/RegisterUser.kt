package com.decrux.pocketr.api.services.user_registration

import com.decrux.pocketr.api.entities.dtos.RegisterUserDto

interface RegisterUser {
    fun registerUser(userDto: RegisterUserDto)
}
