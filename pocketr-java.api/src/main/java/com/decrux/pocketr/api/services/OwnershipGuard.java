package com.decrux.pocketr.api.services;

import com.decrux.pocketr.api.exceptions.ForbiddenException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class OwnershipGuard {

    public void requireOwner(Long resourceOwnerId, long actorId) {
        requireOwner(resourceOwnerId, actorId, "Access denied");
    }

    public void requireOwner(Long resourceOwnerId, long actorId, String message) {
        if (!Objects.equals(resourceOwnerId, actorId)) {
            throw new ForbiddenException(message);
        }
    }
}
