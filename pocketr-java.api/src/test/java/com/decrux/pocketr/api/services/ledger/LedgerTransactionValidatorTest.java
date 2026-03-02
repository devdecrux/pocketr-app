package com.decrux.pocketr.api.services.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.services.ledger.validations.DoubleEntryBalanceValidator;
import com.decrux.pocketr.api.services.ledger.validations.MinimumSplitCountValidator;
import com.decrux.pocketr.api.services.ledger.validations.PositiveSplitAmountValidator;
import com.decrux.pocketr.api.services.ledger.validations.SplitSideValueValidator;
import com.decrux.pocketr.api.services.ledger.validations.TransactionAccountCurrencyValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Ledger validation rules - Isolated split and currency validation")
class LedgerTransactionValidatorTest {

    private MinimumSplitCountValidator minimumSplitCountValidator;
    private PositiveSplitAmountValidator positiveSplitAmountValidator;
    private SplitSideValueValidator splitSideValueValidator;
    private DoubleEntryBalanceValidator doubleEntryBalanceValidator;
    private TransactionAccountCurrencyValidator transactionAccountCurrencyValidator;

    private final Currency eur = new Currency("EUR", (short) 2, "Euro");
    private final Currency usd = new Currency("USD", (short) 2, "US Dollar");
    private final User owner = new User(1L, "encoded", "alice@test.com", null, null, null, new ArrayList<>());

    @BeforeEach
    void setUp() {
        minimumSplitCountValidator = new MinimumSplitCountValidator();
        positiveSplitAmountValidator = new PositiveSplitAmountValidator();
        splitSideValueValidator = new SplitSideValueValidator();
        doubleEntryBalanceValidator = new DoubleEntryBalanceValidator();
        transactionAccountCurrencyValidator = new TransactionAccountCurrencyValidator();
    }

    private void validateSplits(List<CreateSplitDto> splits) {
        minimumSplitCountValidator.validate(splits);
        positiveSplitAmountValidator.validate(splits);
        splitSideValueValidator.validate(splits);
        doubleEntryBalanceValidator.validate(splits);
    }

    private Account account(UUID id, String name, AccountType type, Currency currency) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(name);
        account.setType(type);
        account.setCurrency(currency);
        return account;
    }

    @Nested
    @DisplayName("split validations")
    class ValidateSplits {

        @Test
        @DisplayName("should pass for valid balanced 2-split transaction")
        void validBalancedSplits() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", 5000L, null),
                new CreateSplitDto(UUID.randomUUID(), "CREDIT", 5000L, null)
            );

            assertDoesNotThrow(() -> validateSplits(splits));
        }

        @Test
        @DisplayName("should pass for valid balanced 3-split transaction")
        void validBalancedThreeSplits() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "CREDIT", 10000L, null),
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", 7000L, null),
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", 3000L, null)
            );

            assertDoesNotThrow(() -> validateSplits(splits));
        }

        @Test
        @DisplayName("should reject fewer than 2 splits")
        void rejectFewerThan2() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", 1000L, null)
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> validateSplits(splits));
            assertTrue(ex.getMessage().contains("at least 2 splits"));
        }

        @Test
        @DisplayName("should reject empty splits")
        void rejectEmpty() {
            assertThrows(BadRequestException.class, () -> validateSplits(List.of()));
        }

        @Test
        @DisplayName("should reject zero amount")
        void rejectZeroAmount() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", 0L, null),
                new CreateSplitDto(UUID.randomUUID(), "CREDIT", 0L, null)
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> validateSplits(splits));
            assertTrue(ex.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("should reject negative amount")
        void rejectNegativeAmount() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", -500L, null),
                new CreateSplitDto(UUID.randomUUID(), "CREDIT", -500L, null)
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> validateSplits(splits));
            assertTrue(ex.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("should reject invalid split side")
        void rejectInvalidSide() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "INVALID", 1000L, null),
                new CreateSplitDto(UUID.randomUUID(), "CREDIT", 1000L, null)
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> validateSplits(splits));
            assertTrue(ex.getMessage().contains("Invalid split side"));
        }

        @Test
        @DisplayName("should reject unbalanced debits and credits")
        void rejectUnbalanced() {
            List<CreateSplitDto> splits = List.of(
                new CreateSplitDto(UUID.randomUUID(), "DEBIT", 5000L, null),
                new CreateSplitDto(UUID.randomUUID(), "CREDIT", 4500L, null)
            );

            BadRequestException ex = assertThrows(BadRequestException.class, () -> validateSplits(splits));
            assertTrue(ex.getMessage().contains("Double-entry violation"));
        }
    }

    @Nested
    @DisplayName("currency validation")
    class ValidateCurrencyConsistency {

        @Test
        @DisplayName("should pass when all accounts match transaction currency")
        void allAccountsMatch() {
            List<Account> accounts = List.of(
                account(UUID.randomUUID(), "A1", AccountType.ASSET, eur),
                account(UUID.randomUUID(), "A2", AccountType.EXPENSE, eur)
            );

            assertDoesNotThrow(() -> transactionAccountCurrencyValidator.validate(accounts, "EUR"));
        }

        @Test
        @DisplayName("should reject when an account has different currency")
        void rejectMismatch() {
            List<Account> accounts = List.of(
                account(UUID.randomUUID(), "EUR Acct", AccountType.ASSET, eur),
                account(UUID.randomUUID(), "USD Acct", AccountType.ASSET, usd)
            );

            BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> transactionAccountCurrencyValidator.validate(accounts, "EUR")
            );
            assertTrue(ex.getMessage().contains("currency"));
        }
    }
}
