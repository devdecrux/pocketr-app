package com.decrux.pocketr.api.services.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit;
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.entities.dtos.BalanceDto;
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto;
import com.decrux.pocketr.api.entities.dtos.TransactionDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.CategoryTagRepository;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import com.decrux.pocketr.api.repositories.LedgerSplitRepository;
import com.decrux.pocketr.api.repositories.LedgerTxnRepository;
import com.decrux.pocketr.api.repositories.projections.AccountRawBalanceProjection;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ManageLedgerImpl - Transaction Validation")
class LedgerTransactionValidationTest {

    private LedgerTxnRepository ledgerTxnRepository;
    private LedgerSplitRepository ledgerSplitRepository;
    private AccountRepository accountRepository;
    private CurrencyRepository currencyRepository;
    private CategoryTagRepository categoryTagRepository;
    private ManageHousehold manageHousehold;
    private UserAvatarService userAvatarService;
    private ManageLedgerImpl service;

    private final Currency eur = new Currency("EUR", (short) 2, "Euro");
    private final Currency usd = new Currency("USD", (short) 2, "US Dollar");

    private final User userA = new User(1L, "encoded", "alice@test.com", null, null, null, new ArrayList<>());
    private final User userB = new User(2L, "encoded", "bob@test.com", null, null, null, new ArrayList<>());

    private final UUID checkingId = UUID.randomUUID();
    private final UUID savingsId = UUID.randomUUID();
    private final UUID expenseId = UUID.randomUUID();
    private final UUID incomeId = UUID.randomUUID();
    private final UUID liabilityId = UUID.randomUUID();
    private final UUID equityId = UUID.randomUUID();

    private final Account checking = account(checkingId, userA, "Checking", AccountType.ASSET, eur);
    private final Account savings = account(savingsId, userA, "Savings", AccountType.ASSET, eur);
    private final Account expenseAcct = account(expenseId, userA, "Bills", AccountType.EXPENSE, eur);
    private final Account incomeAcct = account(incomeId, userA, "Salary", AccountType.INCOME, eur);
    private final Account liabilityAcct = account(liabilityId, userA, "Mortgage", AccountType.LIABILITY, eur);
    private final Account equityAcct = account(equityId, userA, "Opening Equity", AccountType.EQUITY, eur);

    private final UUID userBSavingsId = UUID.randomUUID();
    private final Account userBSavings = account(userBSavingsId, userB, "Bob Savings", AccountType.ASSET, eur);

    private final UUID usdAccountId = UUID.randomUUID();
    private final Account usdAccount = account(usdAccountId, userA, "USD Checking", AccountType.ASSET, usd);

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

        when(currencyRepository.findById("EUR")).thenReturn(Optional.of(eur));
        when(currencyRepository.findById("USD")).thenReturn(Optional.of(usd));

        when(ledgerTxnRepository.save(any(LedgerTxn.class))).thenAnswer(invocation -> {
            LedgerTxn txn = invocation.getArgument(0);
            txn.setId(UUID.randomUUID());
            for (LedgerSplit split : txn.getSplits()) {
                split.setId(UUID.randomUUID());
            }
            return txn;
        });
    }

    private Account account(UUID id, User owner, String name, AccountType type, Currency currency) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(name);
        account.setType(type);
        account.setCurrency(currency);
        return account;
    }

    private CategoryTag categoryTag(UUID id, User owner, String name) {
        CategoryTag tag = new CategoryTag();
        tag.setId(id);
        tag.setOwner(owner);
        tag.setName(name);
        return tag;
    }

    private void stubAccounts(Account... accounts) {
        List<UUID> ids = Arrays.stream(accounts).map(Account::getId).filter(Objects::nonNull).toList();
        when(accountRepository.findAllById(ids)).thenReturn(Arrays.asList(accounts));
    }

    private CreateTransactionDto validExpenseDto() {
        return new CreateTransactionDto(
            null,
            null,
            LocalDate.of(2026, 2, 15),
            "EUR",
            "Electricity bill",
            List.of(
                new CreateSplitDto(checkingId, "CREDIT", 4500L, null),
                new CreateSplitDto(expenseId, "DEBIT", 4500L, null)
            )
        );
    }

    @Nested
    @DisplayName("Double-entry invariants")
    class DoubleEntryInvariants {

        @Test
        @DisplayName("should reject transaction with fewer than 2 splits")
        void rejectFewerThan2Splits() {
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Test",
                List.of(new CreateSplitDto(checkingId, "DEBIT", 1000L, null))
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("at least 2 splits"));
        }

        @Test
        @DisplayName("should reject transaction with 0 splits")
        void rejectZeroSplits() {
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Test",
                List.of()
            );

            assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
        }

        @Test
        @DisplayName("should reject transaction where total debits != total credits")
        void rejectDebitsNotEqualCredits() {
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Unbalanced",
                List.of(
                    new CreateSplitDto(checkingId, "DEBIT", 5000L, null),
                    new CreateSplitDto(expenseId, "CREDIT", 4500L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("Double-entry violation"));
        }

        @Test
        @DisplayName("should reject transaction where any split amount is zero")
        void rejectZeroAmount() {
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Zero",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 0L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 0L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("should reject transaction where any split amount is negative")
        void rejectNegativeAmount() {
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Negative",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", -1000L, null),
                    new CreateSplitDto(expenseId, "DEBIT", -1000L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("should reject transaction with invalid split side")
        void rejectInvalidSplitSide() {
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Bad side",
                List.of(
                    new CreateSplitDto(checkingId, "INVALID", 1000L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 1000L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("Invalid split side"));
        }
    }

    @Nested
    @DisplayName("Currency consistency")
    class CurrencyConsistency {

        @Test
        @DisplayName("should reject transaction where account currency != transaction currency")
        void rejectCurrencyMismatch() {
            stubAccounts(usdAccount, expenseAcct);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Mismatch",
                List.of(
                    new CreateSplitDto(usdAccountId, "CREDIT", 4500L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 4500L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("currency"));
        }

        @Test
        @DisplayName("should reject transaction with unknown currency code")
        void rejectUnknownCurrency() {
            when(currencyRepository.findById("XYZ")).thenReturn(Optional.empty());
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "XYZ",
                "Unknown currency",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 1000L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 1000L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("Invalid currency"));
        }
    }

    @Nested
    @DisplayName("Ownership permissions (individual mode)")
    class OwnershipPermissions {

        @Test
        @DisplayName("should reject posting to non-owned account in individual mode")
        void rejectPostingToNonOwnedAccount() {
            stubAccounts(checking, userBSavings);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Not my account",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 5000L, null),
                    new CreateSplitDto(userBSavingsId, "DEBIT", 5000L, null)
                )
            );

            ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("not owned by current user"));
        }

        @Test
        @DisplayName("should allow posting to own accounts in individual mode")
        void allowPostingToOwnAccounts() {
            stubAccounts(checking, expenseAcct);
            CreateTransactionDto dto = validExpenseDto();

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
            assertEquals(2, result.getSplits().size());
            verify(ledgerTxnRepository).save(any(LedgerTxn.class));
        }

        @Test
        @DisplayName("should reject transaction with category tag owned by another user")
        void rejectCategoryTagOwnedByAnotherUser() {
            stubAccounts(checking, expenseAcct);
            UUID bobTagId = UUID.randomUUID();
            CategoryTag bobTag = categoryTag(bobTagId, userB, "Bob's Food");
            when(categoryTagRepository.findAllById(List.of(bobTagId))).thenReturn(List.of(bobTag));

            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "With someone else's tag",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 5000L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 5000L, bobTagId)
                )
            );

            ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("not owned by current user"));
        }

        @Test
        @DisplayName("should reject transaction with non-existent category tag")
        void rejectNonExistentCategoryTag() {
            stubAccounts(checking, expenseAcct);
            UUID missingTagId = UUID.randomUUID();
            when(categoryTagRepository.findAllById(List.of(missingTagId))).thenReturn(List.of());

            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Missing tag",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 5000L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 5000L, missingTagId)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("Category tags not found"));
        }

        @Test
        @DisplayName("should reject transaction with non-existent account")
        void rejectNonExistentAccount() {
            UUID missingId = UUID.randomUUID();
            when(accountRepository.findAllById(List.of(checkingId, missingId))).thenReturn(List.of(checking));

            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Missing account",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 5000L, null),
                    new CreateSplitDto(missingId, "DEBIT", 5000L, null)
                )
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createTransaction(dto, userA));
            assertTrue(ex.getMessage().contains("Accounts not found"));
        }
    }

    @Nested
    @DisplayName("Valid transaction scenarios")
    class ValidTransactionScenarios {

        @Test
        @DisplayName("should succeed for expense transaction (ASSET credit, EXPENSE debit)")
        void validExpenseTransaction() {
            stubAccounts(checking, expenseAcct);
            UUID tagId = UUID.randomUUID();
            CategoryTag tag = categoryTag(tagId, userA, "Electricity");
            when(categoryTagRepository.findAllById(List.of(tagId))).thenReturn(List.of(tag));

            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.of(2026, 2, 15),
                "EUR",
                "Electricity bill",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 4500L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 4500L, tagId)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
            assertEquals("EUR", result.getCurrency());
            assertEquals("Electricity bill", result.getDescription());
            assertEquals(2, result.getSplits().size());
        }

        @Test
        @DisplayName("should succeed for income transaction (INCOME credit, ASSET debit)")
        void validIncomeTransaction() {
            stubAccounts(checking, incomeAcct);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.of(2026, 1, 31),
                "EUR",
                "January salary",
                List.of(
                    new CreateSplitDto(checkingId, "DEBIT", 200000L, null),
                    new CreateSplitDto(incomeId, "CREDIT", 200000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
            assertEquals(2, result.getSplits().size());
        }

        @Test
        @DisplayName("should succeed for transfer between own ASSET accounts")
        void validTransferTransaction() {
            stubAccounts(checking, savings);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Move to savings",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 30000L, null),
                    new CreateSplitDto(savingsId, "DEBIT", 30000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
        }

        @Test
        @DisplayName("should succeed for liability payment (ASSET credit, LIABILITY debit)")
        void validLiabilityPayment() {
            stubAccounts(checking, liabilityAcct);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Mortgage payment",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 50000L, null),
                    new CreateSplitDto(liabilityId, "DEBIT", 50000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
        }

        @Test
        @DisplayName("should succeed for opening balance (EQUITY credit, ASSET debit)")
        void validOpeningBalance() {
            stubAccounts(checking, equityAcct);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Opening balance",
                List.of(
                    new CreateSplitDto(checkingId, "DEBIT", 100000L, null),
                    new CreateSplitDto(equityId, "CREDIT", 100000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
        }

        @Test
        @DisplayName("should succeed for multi-split expense transaction")
        void validMultiSplitExpense() {
            UUID foodId = UUID.randomUUID();
            UUID householdExpId = UUID.randomUUID();
            Account food = account(foodId, userA, "Food", AccountType.EXPENSE, eur);
            Account householdExp = account(householdExpId, userA, "Household", AccountType.EXPENSE, eur);
            stubAccounts(checking, food, householdExp);

            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Grocery trip",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 10000L, null),
                    new CreateSplitDto(foodId, "DEBIT", 7000L, null),
                    new CreateSplitDto(householdExpId, "DEBIT", 3000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
            assertEquals(3, result.getSplits().size());
        }

        @Test
        @DisplayName("should trim description whitespace")
        void trimDescription() {
            stubAccounts(checking, expenseAcct);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "  Trimmed  ",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 1000L, null),
                    new CreateSplitDto(expenseId, "DEBIT", 1000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertEquals("Trimmed", result.getDescription());
        }

        @Test
        @DisplayName("should allow transaction with null category tags")
        void allowNullCategoryTags() {
            stubAccounts(checking, savings);
            CreateTransactionDto dto = new CreateTransactionDto(
                null,
                null,
                LocalDate.now(),
                "EUR",
                "Transfer",
                List.of(
                    new CreateSplitDto(checkingId, "CREDIT", 5000L, null),
                    new CreateSplitDto(savingsId, "DEBIT", 5000L, null)
                )
            );

            TransactionDto result = service.createTransaction(dto, userA);
            assertNotNull(result.getId());
            verify(categoryTagRepository, never()).findAllById(anyList());
        }
    }

    @Nested
    @DisplayName("Balance computation")
    class BalanceComputation {

        @Test
        @DisplayName("should compute batch balances with one grouped query")
        void computeBatchBalancesWithGroupedQuery() {
            LocalDate asOf = LocalDate.of(2026, 2, 20);
            List<UUID> ids = List.of(checkingId, liabilityId, expenseId);
            when(accountRepository.findAllById(ids)).thenReturn(List.of(checking, liabilityAcct, expenseAcct));
            when(ledgerSplitRepository.computeRawBalancesByAccountIds(ids, asOf, SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(
                    List.of(
                        new AccountRawBalanceProjection(checkingId, 190000L),
                        new AccountRawBalanceProjection(liabilityId, -49950000L)
                    )
                );

            List<BalanceDto> result = service.getAccountBalances(ids, asOf, userA, null);
            Map<UUID, BalanceDto> byId = result
                .stream()
                .collect(Collectors.toMap(BalanceDto::getAccountId, Function.identity()));

            assertEquals(3, result.size());
            assertEquals(190000L, byId.get(checkingId).getBalanceMinor());
            assertEquals(49950000L, byId.get(liabilityId).getBalanceMinor());
            assertEquals(0L, byId.get(expenseId).getBalanceMinor());
            verify(ledgerSplitRepository, times(1))
                .computeRawBalancesByAccountIds(ids, asOf, SplitSide.DEBIT, SplitSide.CREDIT);
            verifyNoMoreInteractions(ledgerSplitRepository);
        }

        @Test
        @DisplayName("should reject batch balance query for non-owned account")
        void rejectBatchBalanceForNonOwnedAccount() {
            List<UUID> ids = List.of(checkingId, userBSavingsId);
            when(accountRepository.findAllById(ids)).thenReturn(List.of(checking, userBSavings));

            ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> service.getAccountBalances(ids, LocalDate.now(), userA, null)
            );
            assertTrue(ex.getMessage().contains("Not the owner"));
            verifyNoInteractions(ledgerSplitRepository);
        }

        @Test
        @DisplayName("should reject household batch query when any account is not shared")
        void rejectHouseholdBatchWhenAnyAccountNotShared() {
            UUID householdId = UUID.randomUUID();
            List<UUID> ids = List.of(checkingId, savingsId);
            when(accountRepository.findAllById(ids)).thenReturn(List.of(checking, savings));
            when(manageHousehold.isActiveMember(householdId, userA.getUserId())).thenReturn(true);
            when(manageHousehold.getSharedAccountIds(householdId)).thenReturn(Set.of(checkingId));

            ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> service.getAccountBalances(ids, LocalDate.now(), userA, householdId)
            );
            assertTrue(ex.getMessage().contains("not shared"));
            verifyNoInteractions(ledgerSplitRepository);
        }

        @Test
        @DisplayName("should return 404 when any account is missing in batch query")
        void batchNotFoundForMissingAccount() {
            UUID missingId = UUID.randomUUID();
            List<UUID> ids = List.of(checkingId, missingId);
            when(accountRepository.findAllById(ids)).thenReturn(List.of(checking));

            assertThrows(NotFoundException.class, () -> service.getAccountBalances(ids, LocalDate.now(), userA, null));
            verifyNoInteractions(ledgerSplitRepository);
        }

        @Test
        @DisplayName("should compute debit-normal balance for ASSET account")
        void debitNormalBalanceForAsset() {
            LocalDate asOf = LocalDate.now();
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking));
            when(ledgerSplitRepository.computeBalance(checkingId, asOf, SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(190000L);

            BalanceDto result = service.getAccountBalance(checkingId, asOf, userA, null);
            assertEquals(190000L, result.getBalanceMinor());
            assertEquals("ASSET", result.getAccountType());
            assertEquals("Checking", result.getAccountName());
            assertEquals("EUR", result.getCurrency());
        }

        @Test
        @DisplayName("should compute debit-normal balance for EXPENSE account")
        void debitNormalBalanceForExpense() {
            LocalDate asOf = LocalDate.now();
            when(accountRepository.findById(expenseId)).thenReturn(Optional.of(expenseAcct));
            when(ledgerSplitRepository.computeBalance(expenseId, asOf, SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(8500L);

            BalanceDto result = service.getAccountBalance(expenseId, asOf, userA, null);
            assertEquals(8500L, result.getBalanceMinor());
            assertEquals("EXPENSE", result.getAccountType());
        }

        @Test
        @DisplayName("should compute credit-normal balance for LIABILITY account")
        void creditNormalBalanceForLiability() {
            LocalDate asOf = LocalDate.now();
            when(accountRepository.findById(liabilityId)).thenReturn(Optional.of(liabilityAcct));
            when(ledgerSplitRepository.computeBalance(liabilityId, asOf, SplitSide.CREDIT, SplitSide.DEBIT))
                .thenReturn(49950000L);

            BalanceDto result = service.getAccountBalance(liabilityId, asOf, userA, null);
            assertEquals(49950000L, result.getBalanceMinor());
            assertEquals("LIABILITY", result.getAccountType());
        }

        @Test
        @DisplayName("should compute credit-normal balance for INCOME account")
        void creditNormalBalanceForIncome() {
            LocalDate asOf = LocalDate.now();
            when(accountRepository.findById(incomeId)).thenReturn(Optional.of(incomeAcct));
            when(ledgerSplitRepository.computeBalance(incomeId, asOf, SplitSide.CREDIT, SplitSide.DEBIT))
                .thenReturn(400000L);

            BalanceDto result = service.getAccountBalance(incomeId, asOf, userA, null);
            assertEquals(400000L, result.getBalanceMinor());
            assertEquals("INCOME", result.getAccountType());
        }

        @Test
        @DisplayName("should compute credit-normal balance for EQUITY account")
        void creditNormalBalanceForEquity() {
            LocalDate asOf = LocalDate.now();
            when(accountRepository.findById(equityId)).thenReturn(Optional.of(equityAcct));
            when(ledgerSplitRepository.computeBalance(equityId, asOf, SplitSide.CREDIT, SplitSide.DEBIT))
                .thenReturn(150000L);

            BalanceDto result = service.getAccountBalance(equityId, asOf, userA, null);
            assertEquals(150000L, result.getBalanceMinor());
            assertEquals("EQUITY", result.getAccountType());
        }

        @Test
        @DisplayName("should use provided as-of date for balance query")
        void usesAsOfDate() {
            LocalDate asOf = LocalDate.of(2026, 1, 31);
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking));
            when(ledgerSplitRepository.computeBalance(checkingId, asOf, SplitSide.DEBIT, SplitSide.CREDIT))
                .thenReturn(100000L);

            BalanceDto result = service.getAccountBalance(checkingId, asOf, userA, null);
            assertEquals(100000L, result.getBalanceMinor());
            assertEquals(asOf, result.getAsOf());
        }

        @Test
        @DisplayName("should reject balance query for non-owned account")
        void rejectBalanceForNonOwnedAccount() {
            when(accountRepository.findById(checkingId)).thenReturn(Optional.of(checking));

            assertThrows(
                ForbiddenException.class,
                () -> service.getAccountBalance(checkingId, LocalDate.now(), userB, null)
            );
        }

        @Test
        @DisplayName("should return 404 for non-existent account balance query")
        void notFoundForMissingAccount() {
            UUID missingId = UUID.randomUUID();
            when(accountRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThrows(
                NotFoundException.class,
                () -> service.getAccountBalance(missingId, LocalDate.now(), userA, null)
            );
        }
    }
}
