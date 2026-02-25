package com.decrux.pocketr_api.exceptions

import org.springframework.http.HttpStatus

open class DomainHttpException(
    val status: HttpStatus,
    message: String,
) : RuntimeException(message)

class DomainBadRequestException(message: String) : DomainHttpException(HttpStatus.BAD_REQUEST, message)

class DomainForbiddenException(message: String) : DomainHttpException(HttpStatus.FORBIDDEN, message)

class DomainNotFoundException(message: String) : DomainHttpException(HttpStatus.NOT_FOUND, message)
