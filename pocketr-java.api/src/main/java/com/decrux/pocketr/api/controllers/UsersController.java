package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.RegisterUserDto;
import com.decrux.pocketr.api.entities.dtos.UserDto;
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService;
import com.decrux.pocketr.api.services.user_registration.RegisterUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/user")
public class UsersController {

    private final RegisterUser registerUser;
    private final UserAvatarService userAvatarService;

    public UsersController(RegisterUser registerUser, UserAvatarService userAvatarService) {
        this.registerUser = registerUser;
        this.userAvatarService = userAvatarService;
    }

    @GetMapping
    public UserDto retrieveUserData(@AuthenticationPrincipal User user) {
        return userAvatarService.toUserDto(user);
    }

    @PostMapping("/register")
    public void registerUser(@RequestBody RegisterUserDto registerUserDto) {
        registerUser.registerUser(registerUserDto);
    }

    @PostMapping("/avatar")
    public UserDto uploadAvatar(
            @RequestParam("avatar") MultipartFile avatar,
            @AuthenticationPrincipal User user
    ) {
        return userAvatarService.uploadAvatar(user, avatar);
    }
}
