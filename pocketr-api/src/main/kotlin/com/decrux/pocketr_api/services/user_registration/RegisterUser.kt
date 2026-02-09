package com.decrux.pocketr_api.services.user_registration

import com.decrux.pocketr_api.entities.dtos.RegisterUserDto

interface RegisterUser {

    fun registerUser(userDto: RegisterUserDto)
}
