package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("LedgerSplitRepository DataJpa")
class LedgerSplitRepositoryDataJpaTest {

    @Autowired
    private LedgerSplitRepository ledgerSplitRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    @DisplayName("computeBalance returns debit-credit net up to asOf")
    void computeBalanceReturnsNet() {
        User owner = testEntityManager.persist(RepositoryTestFixtures.user("owner@example.com"));
        Currency eur = testEntityManager.persist(RepositoryTestFixtures.eur());
        Account checking = testEntityManager.persist(RepositoryTestFixtures.account(owner, "Checking", AccountType.ASSET, eur));

        LedgerTxn jan10 = testEntityManager.persist(
            RepositoryTestFixtures.txn(owner, LocalDate.of(2026, 1, 10), eur, null, "Opening")
        );
        LedgerTxn jan11 = testEntityManager.persist(
            RepositoryTestFixtures.txn(owner, LocalDate.of(2026, 1, 11), eur, null, "Withdrawal")
        );
        LedgerTxn feb01 = testEntityManager.persist(
            RepositoryTestFixtures.txn(owner, LocalDate.of(2026, 2, 1), eur, null, "Future")
        );

        testEntityManager.persist(RepositoryTestFixtures.split(jan10, checking, SplitSide.DEBIT, 1_000, null));
        testEntityManager.persist(RepositoryTestFixtures.split(jan11, checking, SplitSide.CREDIT, 250, null));
        testEntityManager.persist(RepositoryTestFixtures.split(feb01, checking, SplitSide.DEBIT, 900, null));

        testEntityManager.flush();
        testEntityManager.clear();

        long balance = ledgerSplitRepository.computeBalance(
            checking.getId(),
            LocalDate.of(2026, 1, 31),
            SplitSide.DEBIT,
            SplitSide.CREDIT
        );

        assertEquals(750, balance);
    }

    @Test
    @DisplayName("monthlyExpensesByUser returns grouped expense projection")
    void monthlyExpensesByUserReturnsProjection() {
        User owner = testEntityManager.persist(RepositoryTestFixtures.user("owner2@example.com"));
        Currency eur = testEntityManager.persist(RepositoryTestFixtures.eur());
        Account groceries = testEntityManager.persist(
            RepositoryTestFixtures.account(owner, "Groceries", AccountType.EXPENSE, eur)
        );
        CategoryTag category = testEntityManager.persist(RepositoryTestFixtures.category(owner, "Food"));

        LedgerTxn januaryTxn = testEntityManager.persist(
            RepositoryTestFixtures.txn(owner, LocalDate.of(2026, 1, 15), eur, null, "Jan grocery")
        );
        LedgerTxn februaryTxn = testEntityManager.persist(
            RepositoryTestFixtures.txn(owner, LocalDate.of(2026, 2, 10), eur, null, "Feb grocery")
        );

        testEntityManager.persist(RepositoryTestFixtures.split(januaryTxn, groceries, SplitSide.DEBIT, 500, category));
        testEntityManager.persist(RepositoryTestFixtures.split(februaryTxn, groceries, SplitSide.DEBIT, 700, category));

        testEntityManager.flush();
        testEntityManager.clear();

        List<MonthlyExpenseProjection> projections = ledgerSplitRepository.monthlyExpensesByUser(
            owner.getUserId(),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 1),
            SplitSide.DEBIT,
            SplitSide.CREDIT
        );

        assertEquals(1, projections.size());
        MonthlyExpenseProjection projection = projections.getFirst();
        assertEquals(groceries.getId(), projection.expenseAccountId());
        assertEquals("Groceries", projection.expenseAccountName());
        assertEquals(category.getId(), projection.categoryTagId());
        assertEquals("food", projection.categoryTagName());
        assertEquals("EUR", projection.currency());
        assertEquals(500, projection.netMinor());
    }
}
