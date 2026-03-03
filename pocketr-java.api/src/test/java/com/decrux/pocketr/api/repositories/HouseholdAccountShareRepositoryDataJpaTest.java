package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.household.Household;
import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShare;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("HouseholdAccountShareRepository DataJpa")
class HouseholdAccountShareRepositoryDataJpaTest {

    @Autowired
    private HouseholdAccountShareRepository householdAccountShareRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("findByHouseholdIdWithAccountAndOwner fetches account owner")
    void findByHouseholdIdWithAccountAndOwnerFetchesOwner() {
        User owner = testEntityManager.persist(RepositoryTestFixtures.user("owner-share@example.com"));
        Currency eur = testEntityManager.persist(RepositoryTestFixtures.eur());
        Account account = testEntityManager.persist(
            RepositoryTestFixtures.account(owner, "Shared Checking", AccountType.ASSET, eur)
        );
        Household household = testEntityManager.persist(RepositoryTestFixtures.household(owner, "Family"));
        testEntityManager.persist(RepositoryTestFixtures.share(household, account, owner));

        testEntityManager.flush();
        testEntityManager.clear();

        List<HouseholdAccountShare> shares = householdAccountShareRepository.findByHouseholdIdWithAccountAndOwner(household.getId());

        assertEquals(1, shares.size());
        HouseholdAccountShare loadedShare = shares.getFirst();
        assertEquals(account.getId(), loadedShare.getAccount().getId());
        assertTrue(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loadedShare.getAccount(), "owner"));
    }

    @Test
    @DisplayName("findSharedAccountIdsByHouseholdId returns all shared account ids")
    void findSharedAccountIdsByHouseholdIdReturnsIds() {
        User owner = testEntityManager.persist(RepositoryTestFixtures.user("owner-share2@example.com"));
        Currency eur = testEntityManager.persist(RepositoryTestFixtures.eur());
        Account first = testEntityManager.persist(RepositoryTestFixtures.account(owner, "A1", AccountType.ASSET, eur));
        Account second = testEntityManager.persist(RepositoryTestFixtures.account(owner, "A2", AccountType.ASSET, eur));
        Household household = testEntityManager.persist(RepositoryTestFixtures.household(owner, "Shared Home"));

        testEntityManager.persist(RepositoryTestFixtures.share(household, first, owner));
        testEntityManager.persist(RepositoryTestFixtures.share(household, second, owner));

        testEntityManager.flush();
        testEntityManager.clear();

        Set<java.util.UUID> ids = householdAccountShareRepository.findSharedAccountIdsByHouseholdId(household.getId());

        assertEquals(2, ids.size());
        assertTrue(ids.contains(first.getId()));
        assertTrue(ids.contains(second.getId()));
    }
}
