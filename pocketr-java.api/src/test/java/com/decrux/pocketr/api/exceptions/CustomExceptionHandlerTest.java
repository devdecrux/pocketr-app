package com.decrux.pocketr.api.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CustomExceptionHandler")
class CustomExceptionHandlerTest {

    private final CustomExceptionHandler handler = new CustomExceptionHandler();

    @Test
    @DisplayName("maps bad request exception to 400")
    void mapsBadRequestExceptionToResponseStatusException() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> handler.handleIllegalArgumentException(new BadRequestException("Invalid payload"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid payload", ex.getReason());
    }

    @Test
    @DisplayName("maps forbidden exception to 403")
    void mapsForbiddenExceptionToResponseStatusException() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> handler.handleAccessDeniedException(new ForbiddenException("Not an active member of this household"))
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Not an active member of this household", ex.getReason());
    }

    @Test
    @DisplayName("maps not found exception to 404")
    void mapsNotFoundExceptionToResponseStatusException() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> handler.handleNoSuchElementException(new NotFoundException("Account not found"))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Account not found", ex.getReason());
    }

    @Test
    @DisplayName("maps authentication exception to 401")
    void mapsAuthenticationExceptionToResponseStatusException() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> handler.handleAuthenticationException(new UserNotFoundException("User with email test@test.com not found"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("User with email test@test.com not found", ex.getReason());
    }
}
