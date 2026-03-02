package com.decrux.pocketr.api.exceptions;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgumentException(IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(AccessDeniedException ex) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public void handleNoSuchElementException(NoSuchElementException ex) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public void handleAuthenticationException(AuthenticationException ex) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
    }
}
