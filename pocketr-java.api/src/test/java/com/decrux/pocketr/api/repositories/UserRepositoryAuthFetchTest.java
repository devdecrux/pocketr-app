package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.auth.UserRole;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository auth fetch strategy")
class UserRepositoryAuthFetchTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("findUserByEmail loads roles for auth path")
    void findUserByEmailLoadsRoles() {
        persistUserWithRole("auth-fetch@example.com");

        User user = userRepository.findUserByEmail("auth-fetch@example.com");

        assertNotNull(user);
        assertTrue(entityManagerFactory.getPersistenceUnitUtil().isLoaded(user, "roles"));
        assertEquals(1, user.getRoles().size());
    }

    @Test
    @DisplayName("findByEmail keeps roles lazy for non-auth path")
    void findByEmailKeepsRolesLazy() {
        persistUserWithRole("lazy-fetch@example.com");

        User user = userRepository.findByEmail("lazy-fetch@example.com").orElseThrow();

        assertFalse(entityManagerFactory.getPersistenceUnitUtil().isLoaded(user, "roles"));
    }

    private void persistUserWithRole(String email) {
        User user = new User();
        user.setPassword("encoded-password");
        user.setEmail(email);
        user.setRoles(new ArrayList<>(List.of(new UserRole(null, "USER"))));

        testEntityManager.persistAndFlush(user);
        testEntityManager.clear();
    }
}
