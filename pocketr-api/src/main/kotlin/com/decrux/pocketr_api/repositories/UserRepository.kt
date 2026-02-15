package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.auth.User
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {

    @EntityGraph(attributePaths = ["roles"])
    fun findUserByEmail(email: String): User?

    fun findByEmail(email: String): Optional<User>
}
