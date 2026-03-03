package com.decrux.pocketr.api.repositories;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.household.Household;
import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShare;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit;
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

final class RepositoryTestFixtures {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    private RepositoryTestFixtures() {
    }

    static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("encoded-password");
        return user;
    }

    static Currency eur() {
        return new Currency("EUR", (short) 2, "Euro");
    }

    static Account account(User owner, String name, AccountType type, Currency currency) {
        Account account = new Account();
        account.setOwner(owner);
        account.setName(name);
        account.setType(type);
        account.setCurrency(currency);
        account.setCreatedAt(FIXED_INSTANT);
        return account;
    }

    static CategoryTag category(User owner, String name) {
        CategoryTag categoryTag = new CategoryTag();
        categoryTag.setOwner(owner);
        categoryTag.setName(name);
        categoryTag.setColor(null);
        categoryTag.setCreatedAt(FIXED_INSTANT);
        return categoryTag;
    }

    static LedgerTxn txn(User creator, LocalDate txnDate, Currency currency, UUID householdId, String description) {
        LedgerTxn txn = new LedgerTxn();
        txn.setCreatedBy(creator);
        txn.setHouseholdId(householdId);
        txn.setTxnDate(txnDate);
        txn.setDescription(description);
        txn.setCurrency(currency);
        txn.setSplits(new ArrayList<>());
        txn.setCreatedAt(FIXED_INSTANT);
        txn.setUpdatedAt(FIXED_INSTANT);
        return txn;
    }

    static LedgerSplit split(LedgerTxn txn, Account account, SplitSide side, long amountMinor, CategoryTag categoryTag) {
        LedgerSplit split = new LedgerSplit();
        split.setTransaction(txn);
        split.setAccount(account);
        split.setSide(side);
        split.setAmountMinor(amountMinor);
        split.setCategoryTag(categoryTag);
        return split;
    }

    static Household household(User createdBy, String name) {
        Household household = new Household();
        household.setName(name);
        household.setCreatedBy(createdBy);
        household.setCreatedAt(FIXED_INSTANT);
        household.setMembers(new ArrayList<>());
        return household;
    }

    static HouseholdAccountShare share(Household household, Account account, User sharedBy) {
        return new HouseholdAccountShare(household, account, sharedBy, FIXED_INSTANT);
    }
}
