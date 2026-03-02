package com.decrux.pocketr.api.services.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.BalanceDto;
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto;
import com.decrux.pocketr.api.entities.dtos.PagedTransactionsDto;
import com.decrux.pocketr.api.entities.dtos.TransactionDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.UserRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import com.decrux.pocketr.api.services.ledger.ManageLedger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpeningBalanceServiceImpl")
class OpeningBalanceServiceImplTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CapturingManageLedger manageLedger = new CapturingManageLedger();

    private final OpeningBalanceServiceImpl service = new OpeningBalanceServiceImpl(
        accountRepository,
        userRepository,
        manageLedger,
        new OwnershipGuard()
    );

    private final User owner = user(1L, "owner@example.com");
    private final Currency eur = new Currency("EUR", (short) 2, "Euro");

    @Test
    @DisplayName("creates positive opening balance as DEBIT(asset)/CREDIT(equity)")
    void createsPositiveOpeningBalance() {
        Account assetAccount = account(UUID.randomUUID(), owner, "Checking", AccountType.ASSET, eur);
        UUID equityAccountId = UUID.randomUUID();

        when(userRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(owner));
        when(accountRepository.findByOwnerUserIdAndTypeAndCurrencyCodeAndName(1L, AccountType.EQUITY, "EUR", "Opening Equity"))
            .thenReturn(null);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(equityAccountId);
            return saved;
        });

        service.createForNewAssetAccount(owner, assetAccount, 100_000L, LocalDate.parse("2026-02-15"));

        assertEquals(1, manageLedger.calls.size());
        LedgerCall call = manageLedger.calls.get(0);
        CreateTransactionDto dto = call.dto();

        assertEquals("INDIVIDUAL", dto.getMode());
        assertEquals(owner.getUserId(), call.creator().getUserId());
        assertEquals("EUR", dto.getCurrency());
        assertEquals(2, dto.getSplits().size());
        assertEquals(assetAccount.getId(), dto.getSplits().get(0).getAccountId());
        assertEquals("DEBIT", dto.getSplits().get(0).getSide());
        assertEquals(100_000L, dto.getSplits().get(0).getAmountMinor());
        assertEquals(equityAccountId, dto.getSplits().get(1).getAccountId());
        assertEquals("CREDIT", dto.getSplits().get(1).getSide());
        assertEquals(100_000L, dto.getSplits().get(1).getAmountMinor());
    }

    @Test
    @DisplayName("creates negative opening balance as CREDIT(asset)/DEBIT(equity)")
    void createsNegativeOpeningBalance() {
        Account assetAccount = account(UUID.randomUUID(), owner, "Checking", AccountType.ASSET, eur);
        Account equityAccount = account(UUID.randomUUID(), owner, "Opening Equity", AccountType.EQUITY, eur);

        when(userRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(owner));
        when(accountRepository.findByOwnerUserIdAndTypeAndCurrencyCodeAndName(1L, AccountType.EQUITY, "EUR", "Opening Equity"))
            .thenReturn(equityAccount);

        service.createForNewAssetAccount(owner, assetAccount, -5_000L, LocalDate.parse("2026-02-15"));

        assertEquals(1, manageLedger.calls.size());
        LedgerCall call = manageLedger.calls.get(0);
        CreateTransactionDto dto = call.dto();

        assertEquals(2, dto.getSplits().size());
        assertEquals(owner.getUserId(), call.creator().getUserId());
        assertEquals("CREDIT", dto.getSplits().get(0).getSide());
        assertEquals(5_000L, dto.getSplits().get(0).getAmountMinor());
        assertEquals("DEBIT", dto.getSplits().get(1).getSide());
        assertEquals(5_000L, dto.getSplits().get(1).getAmountMinor());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("rejects opening balance for non-ASSET account")
    void rejectsNonAsset() {
        Account liability = account(UUID.randomUUID(), owner, "Loan", AccountType.LIABILITY, eur);

        assertThrows(
            BadRequestException.class,
            () -> service.createForNewAssetAccount(owner, liability, 1_000L, LocalDate.parse("2026-02-15"))
        );
        verifyNoInteractions(userRepository);
        assertEquals(0, manageLedger.calls.size());
    }

    @Test
    @DisplayName("rejects opening balance when caller does not own account")
    void rejectsNonOwner() {
        User anotherUser = user(2L, "another@example.com");
        Account assetAccount = account(UUID.randomUUID(), anotherUser, "Checking", AccountType.ASSET, eur);

        assertThrows(
            ForbiddenException.class,
            () -> service.createForNewAssetAccount(owner, assetAccount, 1_000L, LocalDate.parse("2026-02-15"))
        );
        verifyNoInteractions(userRepository);
        assertEquals(0, manageLedger.calls.size());
    }

    private record LedgerCall(
        CreateTransactionDto dto,
        User creator
    ) {
    }

    private static final class CapturingManageLedger implements ManageLedger {

        private final List<LedgerCall> calls = new ArrayList<>();

        @Override
        public TransactionDto createTransaction(CreateTransactionDto dto, User creator) {
            calls.add(new LedgerCall(dto, creator));
            return new TransactionDto(
                UUID.randomUUID(),
                dto.getTxnDate(),
                dto.getCurrency(),
                dto.getDescription(),
                dto.getHouseholdId(),
                "TRANSFER",
                null,
                List.of(),
                Instant.now(),
                Instant.now()
            );
        }

        @Override
        public PagedTransactionsDto listTransactions(
            User user,
            String mode,
            UUID householdId,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID accountId,
            UUID categoryId,
            int page,
            int size
        ) {
            return new PagedTransactionsDto(List.of(), 0, 50, 0, 0);
        }

        @Override
        public List<BalanceDto> getAccountBalances(List<UUID> accountIds, LocalDate asOf, User user, UUID householdId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public BalanceDto getAccountBalance(UUID accountId, LocalDate asOf, User user, UUID householdId) {
            throw new UnsupportedOperationException("Not used in this test");
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
