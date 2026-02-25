package com.decrux.pocketr_api.repositories

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.auth.UserRole
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository auth fetch strategy")
class UserRepositoryAuthFetchTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val testEntityManager: TestEntityManager,
    private val entityManagerFactory: EntityManagerFactory,
) {

    @Test
    @DisplayName("findUserByEmail loads roles for auth path")
    fun findUserByEmailLoadsRoles() {
        persistUserWithRole("auth-fetch@example.com")

        val user = userRepository.findUserByEmail("auth-fetch@example.com")

        assertNotNull(user)
        assertTrue(entityManagerFactory.persistenceUnitUtil.isLoaded(user, "roles"))
        assertEquals(1, user!!.roles.size)
    }

    @Test
    @DisplayName("findByEmail keeps roles lazy for non-auth path")
    fun findByEmailKeepsRolesLazy() {
        persistUserWithRole("lazy-fetch@example.com")

        val user = userRepository.findByEmail("lazy-fetch@example.com").orElseThrow()

        assertFalse(entityManagerFactory.persistenceUnitUtil.isLoaded(user, "roles"))
    }

    private fun persistUserWithRole(email: String) {
        val user = User(
            passwordValue = "encoded-password",
            email = email,
            roles = mutableListOf(UserRole(role = "USER")),
        )
        testEntityManager.persistAndFlush(user)
        testEntityManager.clear()
    }
}
