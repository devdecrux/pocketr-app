package com.decrux.pocketr.api.services.user_registration;

import com.decrux.pocketr.api.entities.dtos.RegisterUserDto;

public interface RegisterUser {
    void registerUser(RegisterUserDto userDto);
}
