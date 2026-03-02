package com.decrux.pocketr.api.services.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateAccountDto;
import com.decrux.pocketr.api.entities.dtos.UpdateAccountDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import com.decrux.pocketr.api.repositories.HouseholdAccountShareRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@DisplayName("ManageAccountImpl")
class ManageAccountImplTest {

    private AccountRepository accountRepository;
    private CurrencyRepository currencyRepository;
    private CapturingOpeningBalanceService openingBalanceService;
    private ManageHousehold manageHousehold;
    private HouseholdAccountShareRepository householdAccountShareRepository;
    private ManageAccountImpl service;

    private final Currency eur = new Currency("EUR", (short) 2, "Euro");
    private final Currency usd = new Currency("USD", (short) 2, "US Dollar");

    private final User ownerUser = user(1L, "alice@example.com");
    private final User otherUser = user(2L, "bob@example.com");

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        currencyRepository = mock(CurrencyRepository.class);
        openingBalanceService = new CapturingOpeningBalanceService();
        manageHousehold = mock(ManageHousehold.class);
        householdAccountShareRepository = mock(HouseholdAccountShareRepository.class);
        service = new ManageAccountImpl(
            accountRepository,
            currencyRepository,
            openingBalanceService,
            manageHousehold,
            householdAccountShareRepository,
            new OwnershipGuard()
        );

        when(currencyRepository.findById("EUR")).thenReturn(Optional.of(eur));
        when(currencyRepository.findById("USD")).thenReturn(Optional.of(usd));
    }

    @Nested
    @DisplayName("createAccount")
    class CreateAccount {

        @Test
        @DisplayName("should create ASSET account with valid inputs")
        void createAssetAccount() {
            CreateAccountDto dto = new CreateAccountDto("Checking", "ASSET", "EUR", null, null);
            UUID savedId = UUID.randomUUID();

            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account account = invocation.getArgument(0);
                account.setId(savedId);
                return account;
            });

            AccountDto result = service.createAccount(dto, ownerUser);

            assertEquals(savedId, result.getId());
            assertEquals(1L, result.getOwnerUserId());
            assertEquals("Checking", result.getName());
            assertEquals("ASSET", result.getType());
            assertEquals("EUR", result.getCurrency());
            assertTrue(openingBalanceService.calls.isEmpty());
        }

        @Test
        @DisplayName("should create accounts for all user-creatable types")
        void createAccountForUserCreatableTypes() {
            List<AccountType> creatableTypes = List.of(
                AccountType.ASSET,
                AccountType.LIABILITY,
                AccountType.INCOME,
                AccountType.EXPENSE
            );

            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account account = invocation.getArgument(0);
                account.setId(UUID.randomUUID());
                return account;
            });

            for (AccountType type : creatableTypes) {
                CreateAccountDto dto = new CreateAccountDto("Test " + type.name(), type.name(), "EUR", null, null);
                AccountDto result = service.createAccount(dto, ownerUser);
                assertEquals(type.name(), result.getType());
            }
            assertTrue(openingBalanceService.calls.isEmpty());
        }

        @Test
        @DisplayName("should reject manual creation of EQUITY accounts")
        void rejectManualEquityCreation() {
            CreateAccountDto dto = new CreateAccountDto("Opening Equity", "EQUITY", "EUR", null, null);

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createAccount(dto, ownerUser));
            assertTrue(ex.getMessage().contains("system-managed"));
            verify(accountRepository, never()).save(any(Account.class));
            assertTrue(openingBalanceService.calls.isEmpty());
        }

        @Test
        @DisplayName("should reject invalid account type")
        void rejectInvalidAccountType() {
            CreateAccountDto dto = new CreateAccountDto("Bad", "INVALID_TYPE", "EUR", null, null);

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createAccount(dto, ownerUser));
            assertTrue(ex.getMessage().contains("Invalid account type"));
        }

        @Test
        @DisplayName("should reject unknown currency code")
        void rejectUnknownCurrency() {
            when(currencyRepository.findById("XYZ")).thenReturn(Optional.empty());
            CreateAccountDto dto = new CreateAccountDto("Checking", "ASSET", "XYZ", null, null);

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createAccount(dto, ownerUser));
            assertTrue(ex.getMessage().contains("Invalid currency"));
        }

        @Test
        @DisplayName("should trim whitespace from account name")
        void trimAccountName() {
            CreateAccountDto dto = new CreateAccountDto("  Checking  ", "ASSET", "EUR", null, null);
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account account = invocation.getArgument(0);
                account.setId(UUID.randomUUID());
                return account;
            });

            AccountDto result = service.createAccount(dto, ownerUser);
            assertEquals("Checking", result.getName());
            assertTrue(openingBalanceService.calls.isEmpty());
        }

        @Test
        @DisplayName("should set owner to the authenticated user")
        void setOwnerToAuthenticatedUser() {
            CreateAccountDto dto = new CreateAccountDto("Checking", "ASSET", "EUR", null, null);
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account account = invocation.getArgument(0);
                account.setId(UUID.randomUUID());
                assertEquals(ownerUser.getUserId(), account.getOwner().getUserId());
                return account;
            });

            service.createAccount(dto, ownerUser);
            verify(accountRepository).save(any(Account.class));
            assertTrue(openingBalanceService.calls.isEmpty());
        }

        @Test
        @DisplayName("should create opening balance transaction when openingBalanceMinor is non-zero")
        void createOpeningBalanceWhenRequested() {
            UUID accountId = UUID.randomUUID();
            LocalDate date = LocalDate.parse("2026-02-15");
            CreateAccountDto dto = new CreateAccountDto("Checking", "ASSET", "EUR", 100_000L, date);

            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
                Account account = invocation.getArgument(0);
                account.setId(accountId);
                return account;
            });

            AccountDto result = service.createAccount(dto, ownerUser);

            assertEquals(accountId, result.getId());
            assertEquals(1, openingBalanceService.calls.size());
            OpeningBalanceCall call = openingBalanceService.calls.get(0);
            assertEquals(ownerUser.getUserId(), call.owner().getUserId());
            assertEquals(accountId, call.account().getId());
            assertEquals(100_000L, call.amountMinor());
            assertEquals(date, call.txnDate());
        }

        @Test
        @DisplayName("should reject opening balance for non-ASSET account type")
        void rejectOpeningBalanceForNonAssetType() {
            CreateAccountDto dto = new CreateAccountDto("Salary", "INCOME", "EUR", 50_000L, null);

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createAccount(dto, ownerUser));
            assertTrue(ex.getMessage().contains("ASSET"));
            verify(accountRepository, never()).save(any(Account.class));
            assertTrue(openingBalanceService.calls.isEmpty());
        }

        @Test
        @DisplayName("should reject openingBalanceDate when openingBalanceMinor is zero")
        void rejectOpeningBalanceDateWithoutAmount() {
            CreateAccountDto dto = new CreateAccountDto("Checking", "ASSET", "EUR", null, LocalDate.parse("2026-02-15"));

            BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createAccount(dto, ownerUser));
            assertTrue(ex.getMessage().contains("openingBalanceDate"));
            verify(accountRepository, never()).save(any(Account.class));
            assertTrue(openingBalanceService.calls.isEmpty());
        }
    }

    @Nested
    @DisplayName("listAccounts")
    class ListAccounts {

        @Test
        @DisplayName("should return owner accounts")
        void returnOwnerAccounts() {
            List<Account> accounts = List.of(
                account(UUID.randomUUID(), ownerUser, "Checking", AccountType.ASSET, eur),
                account(UUID.randomUUID(), ownerUser, "Savings", AccountType.ASSET, eur)
            );
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(accounts);

            List<AccountDto> result = service.listIndividualAccounts(ownerUser);
            assertEquals(2, result.size());
            assertEquals(1L, result.get(0).getOwnerUserId());
            assertEquals(1L, result.get(1).getOwnerUserId());
            assertEquals("Checking", result.get(0).getName());
            assertEquals("Savings", result.get(1).getName());
            verify(accountRepository).findByOwnerUserId(1L);
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void returnEmptyListWhenNoAccounts() {
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(List.of());

            List<AccountDto> result = service.listIndividualAccounts(ownerUser);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updateAccount")
    class UpdateAccount {

        private final UUID accountId = UUID.randomUUID();
        private final Account existingAccount = account(accountId, ownerUser, "Checking", AccountType.ASSET, eur);

        @Test
        @DisplayName("should rename account")
        void renameAccount() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AccountDto result = service.updateAccount(accountId, new UpdateAccountDto("Main Checking"), ownerUser);
            assertEquals("Main Checking", result.getName());
        }

        @Test
        @DisplayName("should reject update by non-owner")
        void rejectUpdateByNonOwner() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

            assertThrows(
                ForbiddenException.class,
                () -> service.updateAccount(accountId, new UpdateAccountDto("Stolen"), otherUser)
            );
        }

        @Test
        @DisplayName("should return 404 for non-existent account")
        void notFoundForMissingAccount() {
            UUID missingId = UUID.randomUUID();
            when(accountRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.updateAccount(missingId, new UpdateAccountDto("X"), ownerUser));
        }

        @Test
        @DisplayName("should trim whitespace when renaming")
        void trimWhitespaceOnRename() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AccountDto result = service.updateAccount(accountId, new UpdateAccountDto("  Savings  "), ownerUser);
            assertEquals("Savings", result.getName());
        }

        @Test
        @DisplayName("should not change fields when update dto has nulls")
        void noChangeWhenDtoFieldsNull() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AccountDto result = service.updateAccount(accountId, new UpdateAccountDto(null), ownerUser);
            assertEquals("Checking", result.getName());
            assertEquals("ASSET", result.getType());
        }
    }

    @Nested
    @DisplayName("listAccounts with mode")
    class ListAccountsWithMode {

        private final UUID householdId = UUID.randomUUID();

        @Test
        @DisplayName("INDIVIDUAL mode returns only owner accounts")
        void individualModeReturnsOwnerAccounts() {
            List<Account> accounts = List.of(account(UUID.randomUUID(), ownerUser, "Checking", AccountType.ASSET, eur));
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(accounts);

            List<AccountDto> result = service.listAccountsByMode(ownerUser, "INDIVIDUAL", null);
            assertEquals(1, result.size());
            assertEquals("Checking", result.get(0).getName());
            verify(accountRepository).findByOwnerUserId(1L);
            verifyNoInteractions(manageHousehold);
            verifyNoInteractions(householdAccountShareRepository);
        }

        @Test
        @DisplayName("HOUSEHOLD mode returns owned + shared accounts")
        void householdModeReturnsOwnedAndSharedAccounts() {
            Account ownedAccount = account(UUID.randomUUID(), ownerUser, "My Checking", AccountType.ASSET, eur);
            Account sharedAccount = account(UUID.randomUUID(), otherUser, "Bob Savings", AccountType.ASSET, eur);

            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true);
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(List.of(ownedAccount));
            when(householdAccountShareRepository.findSharedAccountIdsByHouseholdId(householdId))
                .thenReturn(Set.of(sharedAccount.getId()));
            doReturn(List.of(sharedAccount)).when(accountRepository).findAllById(ArgumentMatchers.<Iterable<UUID>>any());

            List<AccountDto> result = service.listAccountsByMode(ownerUser, "HOUSEHOLD", householdId);
            assertEquals(2, result.size());
            Set<String> names = result.stream().map(AccountDto::getName).collect(Collectors.toSet());
            assertTrue(names.contains("My Checking"));
            assertTrue(names.contains("Bob Savings"));
        }

        @Test
        @DisplayName("HOUSEHOLD mode deduplicates when owned account is also shared")
        void householdModeDeduplicates() {
            Account sharedOwnedAccount = account(UUID.randomUUID(), ownerUser, "Shared Checking", AccountType.ASSET, eur);

            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true);
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(List.of(sharedOwnedAccount));
            when(householdAccountShareRepository.findSharedAccountIdsByHouseholdId(householdId))
                .thenReturn(Set.of(sharedOwnedAccount.getId()));
            doReturn(List.of(sharedOwnedAccount)).when(accountRepository).findAllById(ArgumentMatchers.<Iterable<UUID>>any());

            List<AccountDto> result = service.listAccountsByMode(ownerUser, "HOUSEHOLD", householdId);
            assertEquals(1, result.size());
            assertEquals("Shared Checking", result.get(0).getName());
        }

        @Test
        @DisplayName("HOUSEHOLD mode with no shared accounts returns only owned")
        void householdModeNoSharedAccounts() {
            Account ownedAccount = account(UUID.randomUUID(), ownerUser, "Checking", AccountType.ASSET, eur);

            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(true);
            when(accountRepository.findByOwnerUserId(1L)).thenReturn(List.of(ownedAccount));
            when(householdAccountShareRepository.findSharedAccountIdsByHouseholdId(householdId))
                .thenReturn(Set.of());

            List<AccountDto> result = service.listAccountsByMode(ownerUser, "HOUSEHOLD", householdId);
            assertEquals(1, result.size());
            assertEquals("Checking", result.get(0).getName());
        }

        @Test
        @DisplayName("HOUSEHOLD mode rejects non-member with 403")
        void householdModeRejectsNonMember() {
            when(manageHousehold.isActiveMember(householdId, 1L)).thenReturn(false);

            assertThrows(ForbiddenException.class, () -> service.listAccountsByMode(ownerUser, "HOUSEHOLD", householdId));
            verify(accountRepository, never()).findByOwnerUserId(anyLong());
        }

        @Test
        @DisplayName("HOUSEHOLD mode without householdId returns 400")
        void householdModeWithoutHouseholdId() {
            assertThrows(BadRequestException.class, () -> service.listAccountsByMode(ownerUser, "HOUSEHOLD", null));
        }

        @Test
        @DisplayName("Invalid mode returns 400")
        void invalidModeReturns400() {
            assertThrows(BadRequestException.class, () -> service.listAccountsByMode(ownerUser, "INVALID", null));
        }
    }

    private record OpeningBalanceCall(
        User owner,
        Account account,
        long amountMinor,
        LocalDate txnDate
    ) {
    }

    private static final class CapturingOpeningBalanceService implements OpeningBalanceService {

        private final List<OpeningBalanceCall> calls = new ArrayList<>();

        @Override
        public void createForNewAssetAccount(User owner, Account assetAccount, long openingBalanceMinor, LocalDate txnDate) {
            calls.add(new OpeningBalanceCall(owner, assetAccount, openingBalanceMinor, txnDate));
        }
    }

    private static User user(long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded");
        user.setEmail(email);
        return user;
    }

    private static Account account(UUID id, User owner, String name, AccountType type, Currency currency) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(name);
        account.setType(type);
        account.setCurrency(currency);
        return account;
    }
}
