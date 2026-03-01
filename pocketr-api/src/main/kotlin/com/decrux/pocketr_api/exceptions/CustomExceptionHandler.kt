package com.decrux.pocketr_api.exceptions

import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class CustomExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): Nothing {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message, ex)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): Nothing {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message, ex)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): Nothing {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message, ex)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): Nothing {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.message, ex)
    }
}
