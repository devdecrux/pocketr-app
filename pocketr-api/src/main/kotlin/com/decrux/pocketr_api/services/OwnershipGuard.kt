package com.decrux.pocketr_api.services

import com.decrux.pocketr_api.exceptions.DomainForbiddenException
import org.springframework.stereotype.Component

@Component
class OwnershipGuard {
    fun requireOwner(resourceOwnerId: Long?, actorId: Long, message: String = "Access denied") {
        if (resourceOwnerId != actorId) throw DomainForbiddenException(message)
    }
}
