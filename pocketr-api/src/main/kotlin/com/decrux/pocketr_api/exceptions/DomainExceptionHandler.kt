package com.decrux.pocketr_api.exceptions

import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class DomainExceptionHandler {

    @ExceptionHandler(DomainHttpException::class)
    fun handleDomainHttpException(ex: DomainHttpException): Nothing {
        throw ResponseStatusException(ex.status, ex.message, ex)
    }
}
