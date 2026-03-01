package com.decrux.pocketr.api.exceptions

import org.springframework.security.access.AccessDeniedException

class ForbiddenException(
    message: String,
) : AccessDeniedException(message)
