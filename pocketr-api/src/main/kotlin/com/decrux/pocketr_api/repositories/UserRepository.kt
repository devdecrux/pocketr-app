package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.auth.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    @EntityGraph(attributePaths = ["roles"])
    @Query("select u from User u where u.email = :email")
    fun findUserByEmail(
        @Param("email") email: String,
    ): User?

    fun findByEmail(email: String): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    fun findByUserIdForUpdate(
        @Param("userId") userId: Long,
    ): Optional<User>
}
