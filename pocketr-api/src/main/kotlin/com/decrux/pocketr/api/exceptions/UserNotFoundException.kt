package com.decrux.pocketr.api.exceptions

import org.springframework.security.core.AuthenticationException

class UserNotFoundException(
    message: String,
) : AuthenticationException(message)
