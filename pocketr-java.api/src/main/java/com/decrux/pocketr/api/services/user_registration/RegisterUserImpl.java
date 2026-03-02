package com.decrux.pocketr.api.services.user_registration;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.auth.UserRole;
import com.decrux.pocketr.api.entities.dtos.RegisterUserDto;
import com.decrux.pocketr.api.repositories.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserImpl implements RegisterUser {

    private static final String ROLE_USER = "USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void registerUser(RegisterUserDto userDto) {
        String email = userDto.getEmail().trim();

        UserRole role = new UserRole();
        role.setRole(ROLE_USER);

        User user = new User();
        user.setPassword(requireNotNull(passwordEncoder.encode(userDto.getPassword().trim()), "Password encoder returned null"));
        user.setEmail(email);
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());

        List<UserRole> roles = new ArrayList<>();
        roles.add(role);
        user.setRoles(roles);

        userRepository.saveAndFlush(user);
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
