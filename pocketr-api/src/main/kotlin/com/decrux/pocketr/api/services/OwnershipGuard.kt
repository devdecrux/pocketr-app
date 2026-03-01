package com.decrux.pocketr.api.services

import com.decrux.pocketr.api.exceptions.ForbiddenException
import org.springframework.stereotype.Component

@Component
class OwnershipGuard {
    fun requireOwner(
        resourceOwnerId: Long?,
        actorId: Long,
        message: String = "Access denied",
    ) {
        if (resourceOwnerId != actorId) throw ForbiddenException(message)
    }
}
