package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryTagRepository extends JpaRepository<CategoryTag, UUID> {

    List<CategoryTag> findByOwnerUserId(long userId);

    boolean existsByOwnerUserIdAndNameIgnoreCase(long userId, String name);
}
