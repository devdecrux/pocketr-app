# Initial Balance Implementation Plan (Backend)

This document describes how to implement **Initial Balance** for an **ASSET** account using the **double-entry ledger** architecture and an **EQUITY
“Opening Equity”** account.

> **Rule:** Initial balance is a **ledger transaction**, not an `account.balance` column.

---

## 1) Definition & Invariants

### 1.1 What “initial balance” means

When a user sets an initial balance for a newly created **ASSET** account, the system creates a standard double-entry transaction that increases (or
decreases) the asset and offsets it with an **EQUITY** entry.

### 1.2 Ledger invariants (must always hold)

1. Transaction has **≥ 2 splits**
2. `sum(DEBIT.amount_minor) == sum(CREDIT.amount_minor)` (single currency)
3. `txn.currency == account.currency` for every account referenced by splits (v1 constraint)
4. Posting permissions follow standard rules:
    - Creator may post only to accounts they own.
    - Opening balance txn touches only creator-owned accounts (asset + equity).
5. Split amounts are **positive integers** (`amount_minor > 0`) and `side` indicates direction.

---

## 2) Data Model Requirements

No schema change is strictly required if you already have:

- `account(owner_user_id, type, currency, name)`
- `ledger_txn`
- `ledger_split`

### 2.1 Ensure EQUITY account type exists

`account.type` must support:

- `ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, **`EQUITY`**

### 2.2 Recommended index / constraint (best practice)

To avoid duplicate “Opening Equity” accounts under concurrency:

**Unique constraint**

- `(owner_user_id, type, currency, name)`

or at least `(owner_user_id, type, currency)` if you hardcode name, but name included is best.

---

## 3) Service Design (Best Practice)

Create a dedicated domain service:

### 3.1 OpeningBalanceService responsibilities

- Create opening balance transaction for an ASSET account
- Fetch or create “Opening Equity” account per `(user, currency)`
- Enforce invariants (or reuse the same ledger validator used for normal transactions)
- Run in a **single DB transaction** to avoid partial state

### 3.2 Suggested public API

- `createOpeningBalance(userId, assetAccountId, amountMinor, txnDate, modeContext?)`

`modeContext` is optional and used only if you decide to stamp `household_id` based on UI mode.

---

## 4) “Opening Equity” Account Get-or-Create

### 4.1 Strategy (recommended)

Maintain one equity account per user per currency:

- owner: `userId`
- type: `EQUITY`
- currency: same as the asset account
- name: constant `"Opening Equity"`

### 4.2 Implementation details

`getOrCreateOpeningEquityAccount(userId, currency)`

Steps:

1. Query for existing `EQUITY` account with:
    - `owner_user_id = userId`
    - `type = 'EQUITY'`
    - `currency = :currency`
    - `name = 'Opening Equity'`
2. If found: return it.
3. If not found: create it and return.

### 4.3 Concurrency best practice

If two requests race:

- With the unique constraint, one insert will fail with conflict.
- Handle it by catching the conflict and re-fetching the existing row.

---

## 5) Posting Logic (Splits)

### 5.1 Positive initial balance

Initial balance `+X` for ASSET:

- Split A (asset account): `DEBIT X`
- Split B (opening equity): `CREDIT X`

### 5.2 Negative initial balance

If user enters `-X`:

- Split A (asset account): `CREDIT X`
- Split B (opening equity): `DEBIT X`

This supports overdraft-like asset balances without special cases.

### 5.3 Zero initial balance

If `amountMinor == 0`:

- Do **not** create a transaction.

---

## 6) API Contract Options

Pick **one** approach.

### Option A (recommended): dedicated endpoint (server constructs splits)

1) `POST /accounts` creates the account
2) If initialBalance != 0:
    - `POST /accounts/{accountId}/opening-balance`

**Request body**

```json
{
  "amountMinor": 100000,
  "txnDate": "2026-02-15",
  "mode": "INDIVIDUAL"
}
```

**Why this is best**

- UI sends an amount; server builds correct splits.
- Prevents client bugs from constructing wrong DEBIT/CREDIT lines.
- Easy to validate and test.

### Option B: one-step account creation (account + optional opening balance)

Extend `POST /accounts`:

```json
{
  "name": "Checking",
  "type": "ASSET",
  "currency": "EUR",
  "openingBalanceMinor": 100000,
  "openingBalanceDate": "2026-02-15"
}
```

Implementation: create account and opening-balance txn in the **same DB transaction**.

---

## 7) Household Mode Considerations (Overlay)

Opening balance is fundamentally **personal** (account ownership does not change).

You must choose one of the following and document it:

### 7.1 Default (recommended v1): household_id is null

- Always create opening balance with `ledger_txn.household_id = null`
- Balance calculations still work at account level.
- Household transaction list may or may not show it depending on household query policy.

### 7.2 If household lists are strict by household_id

If household transaction listing is strictly `where household_id = X`, and you want opening balances visible there:

- When the account is created while user is in household mode, set `ledger_txn.household_id = selectedHouseholdId`.

Still safe because:

- Both accounts in the txn are owned by creator
- It doesn’t violate cross-user posting rules

---

## 8) Validation Checklist (Must Implement)

When creating an opening balance:

1. Asset account exists
2. Asset account `type == 'ASSET'`
3. Asset account is owned by `userId`
4. `amountMinor != 0`
5. Currency:
    - derive `currency` from asset account
    - ensure “Opening Equity” account is same currency
6. Create 2 splits with `amount_minor = abs(amountMinor)` and correct sides
7. Enforce debits == credits (by construction)
8. Persist inside one DB transaction

---

## 9) Test Plan (Mandatory)

### 9.1 Unit tests

- Positive amount: creates DEBIT(asset) + CREDIT(equity)
- Negative amount: creates CREDIT(asset) + DEBIT(equity)
- Zero amount: creates no txn
- Currency mismatch rejected (if currency provided explicitly)
- Only owner can create opening balance
- Opening Equity auto-created on demand
- Concurrency: ensure only one Opening Equity account exists (requires unique constraint test)

### 9.2 Integration tests

- Create account + opening balance → balance query returns expected `balance_minor`
- If you stamp `household_id` based on mode:
    - verify opening balance appears in household transaction list when expected

---

## 10) Implementation Steps (Agent Checklist)

1. Confirm `EQUITY` exists in account type enums and backend validation.
2. Add unique constraint `(owner_user_id, type, currency, name)` for `Opening Equity` (recommended).
3. Implement `OpeningBalanceService`:
    - `getOrCreateOpeningEquityAccount(userId, currency)`
    - `createOpeningBalanceTxn(userId, assetAccountId, amountMinor, txnDate, householdContext?)`
4. Add API endpoint:
    - `POST /accounts/{id}/opening-balance` (recommended)  
      **OR** extend `POST /accounts` with optional opening balance fields.
5. Ensure operation is wrapped in a single DB transaction.
6. Add unit + integration tests.
7. Update API docs / OpenAPI + frontend contract notes.

---

## 11) Example (EUR Checking initial balance 1,000.00)

**Inputs**

- account: Checking (ASSET, EUR)
- initial balance: `100000` (minor units)
- date: `2026-02-15`

**Writes**
`ledger_txn`

- `currency = 'EUR'`
- `txn_date = '2026-02-15'`
- `description = 'Opening balance – Checking'`
- `household_id = null` (or selected household if you chose 7.2)

`ledger_split`

1) Checking: `DEBIT 100000`
2) Opening Equity: `CREDIT 100000`

---

## 12) Notes / Best Practices

- Do **not** store a mutable `account.balance` column as source of truth.
- Prefer “server constructs splits” to prevent client-side accounting mistakes.
- Consider auditability: later you may prefer “reversal transactions” over editing opening balances.
