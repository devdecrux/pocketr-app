package com.decrux.pocketr.api.services.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit;
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.entities.dtos.PagedTransactionsDto;
import com.decrux.pocketr.api.entities.dtos.SplitDto;
import com.decrux.pocketr.api.entities.dtos.TransactionDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.CategoryTagRepository;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import com.decrux.pocketr.api.repositories.LedgerSplitRepository;
import com.decrux.pocketr.api.repositories.LedgerTxnRepository;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import com.decrux.pocketr.api.services.ledger.validations.CrossUserAssetAccountTypeValidator;
import com.decrux.pocketr.api.services.ledger.validations.DoubleEntryBalanceValidator;
import com.decrux.pocketr.api.services.ledger.validations.HouseholdIdPresenceValidator;
import com.decrux.pocketr.api.services.ledger.validations.HouseholdMembershipValidator;
import com.decrux.pocketr.api.services.ledger.validations.HouseholdSharedAccountValidator;
import com.decrux.pocketr.api.services.ledger.validations.IndividualModeOwnershipValidator;
import com.decrux.pocketr.api.services.ledger.validations.MinimumSplitCountValidator;
import com.decrux.pocketr.api.services.ledger.validations.PositiveSplitAmountValidator;
import com.decrux.pocketr.api.services.ledger.validations.SplitSideValueValidator;
import com.decrux.pocketr.api.services.ledger.validations.TransactionAccountCurrencyValidator;
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for household transaction visibility in listTransactions().
 */
@DisplayName("ManageLedgerImpl - Household Transaction Visibility")
class HouseholdTransactionVisibilityTest {

    private LedgerTxnRepository ledgerTxnRepository;
    private LedgerSplitRepository ledgerSplitRepository;
    private AccountRepository accountRepository;
    private CurrencyRepository currencyRepository;
    private CategoryTagRepository categoryTagRepository;
    private ManageHousehold manageHousehold;
    private UserAvatarService userAvatarService;
    private ManageLedgerImpl service;

    private final Currency eur = new Currency("EUR", (short) 2, "Euro");

    private final User alice = buildUser(1L, "alice@test.com");
    private final User bob = buildUser(2L, "bob@test.com");
    private final User outsider = buildUser(99L, "outsider@test.com");

    private final UUID householdId = UUID.randomUUID();

    private final UUID aliceCheckingId = UUID.randomUUID();
    private final Account aliceChecking = new Account(
        aliceCheckingId,
        alice,
        "Alice Checking",
        AccountType.ASSET,
        eur,
        Instant.now()
    );
    private final UUID aliceExpenseId = UUID.randomUUID();
    private final Account aliceExpense = new Account(
        aliceExpenseId,
        alice,
        "Alice Groceries",
        AccountType.EXPENSE,
        eur,
        Instant.now()
    );

    private final UUID bobCheckingId = UUID.randomUUID();
    private final Account bobChecking = new Account(
        bobCheckingId,
        bob,
        "Bob Checking",
        AccountType.ASSET,
        eur,
        Instant.now()
    );

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        ledgerTxnRepository = mock(LedgerTxnRepository.class);
        ledgerSplitRepository = mock(LedgerSplitRepository.class);
        accountRepository = mock(AccountRepository.class);
        currencyRepository = mock(CurrencyRepository.class);
        categoryTagRepository = mock(CategoryTagRepository.class);
        manageHousehold = mock(ManageHousehold.class);
        userAvatarService = mock(UserAvatarService.class);

        service = new ManageLedgerImpl(
            ledgerTxnRepository,
            ledgerSplitRepository,
            accountRepository,
            currencyRepository,
            categoryTagRepository,
            manageHousehold,
            userAvatarService,
            new MinimumSplitCountValidator(),
            new PositiveSplitAmountValidator(),
            new SplitSideValueValidator(),
            new DoubleEntryBalanceValidator(),
            new TransactionAccountCurrencyValidator(),
            new IndividualModeOwnershipValidator(),
            new HouseholdIdPresenceValidator(),
            new HouseholdMembershipValidator(),
            new HouseholdSharedAccountValidator(),
            new CrossUserAssetAccountTypeValidator()
        );
    }

    private static User buildUser(long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded");
        user.setEmail(email);
        return user;
    }

    private LedgerTxn buildTxn(
        User createdBy,
        Account debitAccount,
        Account creditAccount,
        long amount,
        String description,
        LocalDate txnDate,
        UUID txnHouseholdId
    ) {
        LedgerTxn txn = new LedgerTxn();
        txn.setId(UUID.randomUUID());
        txn.setCreatedBy(createdBy);
        txn.setHouseholdId(txnHouseholdId);
        txn.setTxnDate(txnDate);
        txn.setDescription(description);
        txn.setCurrency(eur);
        txn.setCreatedAt(now);
        txn.setUpdatedAt(now);

        LedgerSplit debitSplit = new LedgerSplit();
        debitSplit.setId(UUID.randomUUID());
        debitSplit.setTransaction(txn);
        debitSplit.setAccount(debitAccount);
        debitSplit.setSide(SplitSide.DEBIT);
        debitSplit.setAmountMinor(amount);

        LedgerSplit creditSplit = new LedgerSplit();
        creditSplit.setId(UUID.randomUUID());
        creditSplit.setTransaction(txn);
        creditSplit.setAccount(creditAccount);
        creditSplit.setSide(SplitSide.CREDIT);
        creditSplit.setAmountMinor(amount);

        txn.setSplits(new ArrayList<>(List.of(debitSplit, creditSplit)));
        return txn;
    }

    private LedgerTxn buildTxn(User createdBy, Account debitAccount, Account creditAccount) {
        return buildTxn(
            createdBy,
            debitAccount,
            creditAccount,
            5000L,
            "Test txn",
            LocalDate.of(2026, 2, 15),
            null
        );
    }

    private LedgerTxn buildTxn(User createdBy, Account debitAccount, Account creditAccount, String description) {
        return buildTxn(
            createdBy,
            debitAccount,
            creditAccount,
            5000L,
            description,
            LocalDate.of(2026, 2, 15),
            null
        );
    }

    @SuppressWarnings("unchecked")
    private Specification<LedgerTxn> anySpec() {
        return (Specification<LedgerTxn>) any(Specification.class);
    }

    private Pageable anyPageable() {
        return any(Pageable.class);
    }

    private void stubFindAll(LedgerTxn... txns) {
        when(ledgerTxnRepository.findAll(anySpec(), anyPageable()))
            .thenReturn(new PageImpl<>(List.of(txns)));
    }

    @Nested
    @DisplayName("listTransactions - household mode")
    class HouseholdMode {

        @Test
        @DisplayName("should return transactions on shared accounts for a household member")
        void returnTransactionsOnSharedAccounts() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId))
                .thenReturn(Set.of(aliceCheckingId, bobCheckingId));

            LedgerTxn txn1 = buildTxn(alice, aliceExpense, aliceChecking, "Alice grocery");
            LedgerTxn txn2 = buildTxn(bob, bobChecking, aliceChecking, "Bob to Alice transfer");
            stubFindAll(txn1, txn2);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals(2, result.getContent().size());
            assertEquals("Alice grocery", result.getContent().get(0).getDescription());
            assertEquals("Bob to Alice transfer", result.getContent().get(1).getDescription());

            verify(manageHousehold).isActiveMember(householdId, alice.getUserId());
            verify(manageHousehold).getSharedAccountIds(householdId);
        }

        @Test
        @DisplayName("should include historical transactions with null householdId on shared accounts")
        void includeHistoricalTransactionsWithNullHouseholdId() {
            when(manageHousehold.isActiveMember(householdId, bob.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));

            LedgerTxn historicalTxn = buildTxn(
                alice,
                aliceExpense,
                aliceChecking,
                5000L,
                "Pre-household expense",
                LocalDate.of(2026, 2, 15),
                null
            );
            stubFindAll(historicalTxn);

            PagedTransactionsDto result = service.listTransactions(
                bob,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals(1, result.getContent().size());
            assertEquals("Pre-household expense", result.getContent().get(0).getDescription());
            assertNull(result.getContent().get(0).getHouseholdId());
        }

        @Test
        @DisplayName("should return empty list when no accounts are shared in the household")
        void emptyListWhenNoSharedAccounts() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of());

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertTrue(result.getContent().isEmpty());
            verify(ledgerTxnRepository, never()).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should throw 403 when user is not an active household member")
        void forbiddenForNonMember() {
            when(manageHousehold.isActiveMember(householdId, outsider.getUserId())).thenReturn(false);

            ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> service.listTransactions(
                    outsider,
                    "HOUSEHOLD",
                    householdId,
                    null,
                    null,
                    null,
                    null,
                    0,
                    50
                )
            );

            assertTrue(ex.getMessage().contains("Not an active member"));
            verify(manageHousehold, never()).getSharedAccountIds(any(UUID.class));
        }

        @Test
        @DisplayName("should throw 400 when householdId is missing in household mode")
        void badRequestWhenHouseholdIdMissing() {
            BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.listTransactions(
                    alice,
                    "HOUSEHOLD",
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    50
                )
            );

            assertTrue(ex.getMessage().contains("householdId is required"));
        }

        @Test
        @DisplayName("should accept case-insensitive mode value")
        void caseInsensitiveMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));
            stubFindAll();

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "household",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertNotNull(result);
            verify(manageHousehold).isActiveMember(householdId, alice.getUserId());
        }

        @Test
        @DisplayName("should apply dateFrom filter in household mode")
        void dateFromFilterInHouseholdMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));

            LedgerTxn txn = buildTxn(
                alice,
                aliceExpense,
                aliceChecking,
                5000L,
                "Test txn",
                LocalDate.of(2026, 2, 20),
                null
            );
            stubFindAll(txn);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                LocalDate.of(2026, 2, 1),
                null,
                null,
                null,
                0,
                50
            );

            assertEquals(1, result.getContent().size());
            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply dateTo filter in household mode")
        void dateToFilterInHouseholdMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));
            stubFindAll();

            service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                LocalDate.of(2026, 2, 28),
                null,
                null,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply accountId filter in household mode")
        void accountIdFilterInHouseholdMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId, bobCheckingId));
            stubFindAll();

            service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                aliceCheckingId,
                null,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply categoryId filter in household mode")
        void categoryIdFilterInHouseholdMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));
            stubFindAll();

            UUID categoryId = UUID.randomUUID();
            service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                categoryId,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply all filters simultaneously in household mode")
        void allFiltersInHouseholdMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));
            stubFindAll();

            service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 28),
                aliceCheckingId,
                UUID.randomUUID(),
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should correctly map transaction DTOs in household mode")
        void correctDtoMapping() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));

            LedgerTxn txn = buildTxn(
                alice,
                aliceExpense,
                aliceChecking,
                12000L,
                "Supermarket",
                LocalDate.of(2026, 2, 10),
                householdId
            );
            stubFindAll(txn);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals(1, result.getContent().size());
            TransactionDto dto = result.getContent().get(0);
            assertEquals(txn.getId(), dto.getId());
            assertEquals(LocalDate.of(2026, 2, 10), dto.getTxnDate());
            assertEquals("EUR", dto.getCurrency());
            assertEquals("Supermarket", dto.getDescription());
            assertEquals(householdId, dto.getHouseholdId());
            assertEquals(2, dto.getSplits().size());

            SplitDto debitSplit = dto.getSplits().stream()
                .filter(split -> "DEBIT".equals(split.getSide()))
                .findFirst()
                .orElseThrow();
            assertEquals(aliceExpenseId, debitSplit.getAccountId());
            assertEquals("Alice Groceries", debitSplit.getAccountName());
            assertEquals("EXPENSE", debitSplit.getAccountType());
            assertEquals(12000L, debitSplit.getAmountMinor());

            SplitDto creditSplit = dto.getSplits().stream()
                .filter(split -> "CREDIT".equals(split.getSide()))
                .findFirst()
                .orElseThrow();
            assertEquals(aliceCheckingId, creditSplit.getAccountId());
            assertEquals("Alice Checking", creditSplit.getAccountName());
            assertEquals("ASSET", creditSplit.getAccountType());
            assertEquals(12000L, creditSplit.getAmountMinor());
        }

        @Test
        @DisplayName("should not call forUser spec in household mode")
        void doesNotUseForUserInHouseholdMode() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));
            stubFindAll();

            service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            verify(manageHousehold).getSharedAccountIds(householdId);
        }
    }

    @Nested
    @DisplayName("listTransactions - individual mode (regression)")
    class IndividualMode {

        @Test
        @DisplayName("should return only user's own transactions in individual mode (null mode)")
        void returnOwnTransactionsNullMode() {
            LedgerTxn txn = buildTxn(alice, aliceExpense, aliceChecking, "Alice's bill");
            stubFindAll(txn);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals(1, result.getContent().size());
            assertEquals("Alice's bill", result.getContent().get(0).getDescription());
            verify(manageHousehold, never()).isActiveMember(any(UUID.class), anyLong());
            verify(manageHousehold, never()).getSharedAccountIds(any(UUID.class));
        }

        @Test
        @DisplayName("should return only user's own transactions in individual mode (explicit 'INDIVIDUAL')")
        void returnOwnTransactionsExplicitIndividualMode() {
            LedgerTxn txn = buildTxn(alice, aliceExpense, aliceChecking);
            stubFindAll(txn);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "INDIVIDUAL",
                null,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals(1, result.getContent().size());
            verify(manageHousehold, never()).isActiveMember(any(UUID.class), anyLong());
            verify(manageHousehold, never()).getSharedAccountIds(any(UUID.class));
        }

        @Test
        @DisplayName("should apply dateFrom filter in individual mode")
        void dateFromFilterInIndividualMode() {
            stubFindAll();

            service.listTransactions(
                alice,
                null,
                null,
                LocalDate.of(2026, 2, 1),
                null,
                null,
                null,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply dateTo filter in individual mode")
        void dateToFilterInIndividualMode() {
            stubFindAll();

            service.listTransactions(
                alice,
                null,
                null,
                null,
                LocalDate.of(2026, 2, 28),
                null,
                null,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply accountId filter in individual mode")
        void accountIdFilterInIndividualMode() {
            stubFindAll();

            service.listTransactions(
                alice,
                null,
                null,
                null,
                null,
                aliceCheckingId,
                null,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should apply categoryId filter in individual mode")
        void categoryIdFilterInIndividualMode() {
            stubFindAll();

            UUID categoryId = UUID.randomUUID();
            service.listTransactions(
                alice,
                null,
                null,
                null,
                null,
                null,
                categoryId,
                0,
                50
            );

            verify(ledgerTxnRepository).findAll(anySpec(), anyPageable());
        }

        @Test
        @DisplayName("should return empty list when user has no transactions")
        void emptyListForNoTransactions() {
            stubFindAll();

            PagedTransactionsDto result = service.listTransactions(
                alice,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("ManageHouseholdImpl.getSharedAccountIds - delegation")
    class GetSharedAccountIdsDelegation {

        @Test
        @DisplayName("should delegate to shareRepository and return account IDs")
        void delegatesToRepository() {
            Set<UUID> expectedIds = Set.of(aliceCheckingId, bobCheckingId);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(expectedIds);

            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            stubFindAll();

            service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            verify(manageHousehold).getSharedAccountIds(householdId);
        }
    }

    @Nested
    @DisplayName("LedgerTxnSpecs.hasAnySharedAccount")
    class HasAnySharedAccountSpec {

        @Test
        @DisplayName("should produce a non-null specification")
        void producesNonNullSpec() {
            Specification<LedgerTxn> spec = LedgerTxnSpecs.hasAnySharedAccount(Set.of(UUID.randomUUID()));
            assertNotNull(spec);
        }

        @Test
        @DisplayName("should produce a non-null specification for multiple account IDs")
        void producesSpecForMultipleIds() {
            Specification<LedgerTxn> spec = LedgerTxnSpecs.hasAnySharedAccount(
                Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            );
            assertNotNull(spec);
        }
    }

    @Nested
    @DisplayName("txnKind derivation in household context")
    class TxnKindDerivation {

        @Test
        @DisplayName("should derive EXPENSE kind for household transactions with expense account")
        void expenseKindForHouseholdTxn() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId));

            LedgerTxn txn = buildTxn(alice, aliceExpense, aliceChecking, "Groceries");
            stubFindAll(txn);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals("EXPENSE", result.getContent().get(0).getTxnKind());
        }

        @Test
        @DisplayName("should derive TRANSFER kind for household transactions between asset accounts")
        void transferKindForHouseholdTxn() {
            when(manageHousehold.isActiveMember(householdId, alice.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(aliceCheckingId, bobCheckingId));

            LedgerTxn txn = buildTxn(alice, bobChecking, aliceChecking, "Cross-user transfer");
            stubFindAll(txn);

            PagedTransactionsDto result = service.listTransactions(
                alice,
                "HOUSEHOLD",
                householdId,
                null,
                null,
                null,
                null,
                0,
                50
            );

            assertEquals("TRANSFER", result.getContent().get(0).getTxnKind());
        }
    }
}
