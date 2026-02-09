package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.auth.PocketrUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<PocketrUser, Long> {

    @Query("SELECT u FROM PocketrUser u JOIN FETCH u.roles WHERE u.email = :email")
    fun findUserByEmail(email: String): PocketrUser?

    fun findByEmail(email: String): Optional<PocketrUser>
}
