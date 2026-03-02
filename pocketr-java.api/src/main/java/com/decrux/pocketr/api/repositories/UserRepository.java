package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.auth.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u where u.email = :email")
    User findUserByEmail(@Param("email") String email);

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByUserIdForUpdate(@Param("userId") Long userId);
}
