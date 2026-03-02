package com.decrux.pocketr.api.config.security;

import com.decrux.pocketr.api.exceptions.UserNotFoundException;
import com.decrux.pocketr.api.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserDetails userDetails = userRepository.findUserByEmail(email);
        if (userDetails == null) {
            throw new UserNotFoundException("User with email " + email + " not found");
        }
        return userDetails;
    }
}
