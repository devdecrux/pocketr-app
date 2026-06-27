package com.decrux.pocketr.api.services

import com.decrux.pocketr.api.exceptions.ForbiddenException
import org.springframework.stereotype.Component

@Component
class OwnershipGuard {
    fun requireOwner(
        ownerUserId: Long?,
        requestingUserId: Long,
        message: String = "Access denied",
    ) {
        if (ownerUserId != requestingUserId) throw ForbiddenException(message)
    }
}
