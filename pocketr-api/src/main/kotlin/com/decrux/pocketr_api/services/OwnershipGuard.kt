package com.decrux.pocketr_api.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class OwnershipGuard {
    fun requireOwner(resourceOwnerId: Long?, actorId: Long, message: String = "Access denied") {
        if (resourceOwnerId != actorId) throw ResponseStatusException(HttpStatus.FORBIDDEN, message)
    }
}
