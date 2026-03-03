package com.decrux.pocketr.api.services.user_registration;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.RegisterUserDto;
import com.decrux.pocketr.api.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RegisterUserImpl")
class RegisterUserImplTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RegisterUserImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new RegisterUserImpl(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("should register new user")
    void registerUserSuccess() {
        RegisterUserDto dto = new RegisterUserDto("password123", "  alice@example.com  ", "Alice", "Doe");
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerUser(dto);

        verify(userRepository).findByEmail("alice@example.com");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("should return conflict when email already exists")
    void rejectDuplicateEmailByPrecheck() {
        RegisterUserDto dto = new RegisterUserDto("password123", "alice@example.com", "Alice", "Doe");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(new User()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.registerUser(dto));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already registered"));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("should return conflict when unique constraint is hit on save")
    void rejectDuplicateEmailByDbConstraint() {
        RegisterUserDto dto = new RegisterUserDto("password123", "alice@example.com", "Alice", "Doe");
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.registerUser(dto));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already registered"));
    }
}
