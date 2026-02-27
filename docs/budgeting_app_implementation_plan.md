# Budgeting App v1 Implementation Plan (Double-Entry Ledger + Multi-Currency + Household Overlay)

This document is a step-by-step implementation plan for agents to follow. It defines **domain rules**, **data model**, **service validations**, **API
endpoints**, **query patterns**, **example transactions**, and **work-splitting** guidance.

> **Core principle:** The **ledger is the source of truth** (double-entry). “Budgeting” and “household mode” are **overlays** (filters + permissions +
> planning) that *read* ledger data.

---

## 0) Glossary & Goals

### Goals (v1)

- Implement a **double-entry ledger** with account types: `ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, `EQUITY`.
- Support **multiple currencies** from day 1.
- Implement **household overlay**:
    - Household membership roles: `OWNER`, `ADMIN`, `MEMBER`.
    - Users share specific accounts into a household.
    - Household mode shows shared accounts + transactions (overlay).
    - Cross-user transfers between shared accounts allowed, while preventing unauthorized posting to others’ accounts.
- Implement **category tags** attached to splits (Expense Account + Category Tag UX).
- Enforce **double-entry rule** from the beginning.

### Non-goals (v1)

- Bank sync integrations (optional later).
- Full “budget overlay” planning tables (can be next phase).
- Multi-currency within a single transaction (v1 uses **one currency per transaction**).

---

## 1) Architecture Overview (Modules)

### 1.1 Ledger Module (Authoritative)

- Entities: `Account`, `LedgerTxn`, `LedgerSplit`, `CategoryTag`
- Rules: double-entry, currency consistency, ownership permissions
- Provides: posting, querying balances, summaries

### 1.2 Household Module (Overlay & Permissions)

- Entities: `Household`, `HouseholdMember`, `HouseholdAccountShare`
- Rules: membership roles, account sharing, visibility filters
- Provides: household view (shared + owned), invite workflow (INVITED/ACTIVE)

### 1.3 Budget Overlay Module (Read-only consumer of Ledger; phase 2+)

- Will compute “actuals” from ledger splits based on mode (individual/household).
- Future: budgets per category, charts, allocations.

> **Important separation:**  
> Ledger knows nothing about “charts” or “budgeting”; it exposes clean data for reporting.  
> Household adds *visibility + permission* checks, not alternate ledger data.

---

## 2) Money Representation & Currency Strategy

### 2.1 Amount storage

Store amounts as integers in **minor units**:

- Field: `amount_minor` (e.g. cents for EUR/USD).
- Avoid floating point errors.

### 2.2 Currency table

Currency defines `minor_unit`:

- EUR/USD -> 2
- JPY -> 0
- BHD -> 3

### 2.3 Transaction currency constraints (v1)

- Each **transaction has a single currency**.
- All accounts referenced by splits **must have the same currency** as the transaction.
- Cross-currency transfers are modeled as **two transactions linked by `fx_group_id`** (phase 1.5 optional).

---

## 3) Data Model (Postgres)

> Use your existing `user` table. Replace `app_user(id)` FK with your actual user table.

### 3.1 Currency

```sql
create table currency (
  code char(3) primary key,
  minor_unit smallint not null,
  name text not null
);
```

### 3.2 Household & Members (roles constrained)

```sql
create table household (
  id uuid primary key,
  name text not null,
  created_by uuid not null references app_user(id),
  created_at timestamptz not null default now()
);

create table household_member (
  household_id uuid not null references household(id) on delete cascade,
  user_id uuid not null references app_user(id) on delete cascade,
  role text not null check (role in ('OWNER','ADMIN','MEMBER')),
  status text not null default 'ACTIVE' check (status in ('INVITED','ACTIVE')),
  invited_by uuid null references app_user(id),
  invited_at timestamptz null,
  joined_at timestamptz null,
  primary key (household_id, user_id)
);
```

### 3.3 Accounts (with EQUITY)

```sql
create table account (
  id uuid primary key,
  owner_user_id uuid not null references app_user(id),
  name text not null,
  type text not null check (type in ('ASSET','LIABILITY','INCOME','EXPENSE','EQUITY')),
  currency char(3) not null references currency(code),
  created_at timestamptz not null default now()
);

create index idx_account_owner on account(owner_user_id);
```

> Optional later fields: `institution`, `external_ref` for import/sync mapping.

### 3.4 Account Sharing into Household (explicit shares)

```sql
create table household_account_share (
  household_id uuid not null references household(id) on delete cascade,
  account_id uuid not null references account(id) on delete cascade,
  shared_by uuid not null references app_user(id),
  shared_at timestamptz not null default now(),
  primary key (household_id, account_id)
);

create index idx_share_household on household_account_share(household_id);
```

### 3.5 Category Tags (expense tagging)

```sql
create table category_tag (
  id uuid primary key,
  owner_user_id uuid not null references app_user(id),
  name text not null,
  created_at timestamptz not null default now(),
  unique (owner_user_id, name)
);
```

> If household-scoped tags are needed later, introduce a `scope` model (individual vs household) or `household_id nullable`.

### 3.6 Ledger Transactions + Splits (Double-entry)

```sql
create table ledger_txn (
  id uuid primary key,
  created_by_user_id uuid not null references app_user(id),
  household_id uuid null references household(id),
  txn_date date not null,
  description text not null,
  currency char(3) not null references currency(code),
  fx_group_id uuid null, -- optional: link related FX txns
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table ledger_split (
  id uuid primary key,
  txn_id uuid not null references ledger_txn(id) on delete cascade,
  account_id uuid not null references account(id),
  side text not null check (side in ('DEBIT','CREDIT')),
  amount_minor bigint not null check (amount_minor > 0),
  category_tag_id uuid null references category_tag(id)
);

create index idx_split_txn on ledger_split(txn_id);
create index idx_split_account on ledger_split(account_id);
```

---

## 4) Accounting Rules & Invariants (Enforced in Service Layer)

### 4.1 Double-entry invariant

For each transaction (single currency):

- `sum(amount_minor WHERE side='DEBIT') == sum(amount_minor WHERE side='CREDIT')`
- transaction must have at least **2 splits**

### 4.2 Account normal balance (for reporting)

- Debit-normal: `ASSET`, `EXPENSE`
- Credit-normal: `LIABILITY`, `INCOME`, `EQUITY`

Compute per account:

- If debit-normal: `balance = debits - credits`
- If credit-normal: `balance = credits - debits`

### 4.3 Currency invariant (v1)

- `txn.currency == account.currency` for every split account.

### 4.4 Permissions invariant (individual vs household)

- Base rule: creator may only post to accounts they own.
- Exception: **household transfer rule** (below).

---

## 5) Household Overlay Rules (Sharing + Transfers)

### 5.1 Visibility (read)

In **individual mode**, user sees:

- Accounts where `owner_user_id = me`
- Transactions where creator is me OR transaction affects my accounts (choose policy; recommended: affects my accounts).

In **household mode**, user sees:

- Shared accounts in that household (`household_account_share`)
- Transactions that include at least one shared account and are associated with the household (recommended: set `ledger_txn.household_id` on write
  when created in household mode)

> Implementation choice:
> - Strict: In household mode, list only txns with `household_id = X`.
> - Broad: Also include txns affecting shared accounts even if household_id is null.  
    > For clean UX, use **strict**: set household_id for household operations.

### 5.2 Sharing rules

- Only account **owner** can share/unshare their account.
- Household OWNER/ADMIN can invite members (invite acceptance next step).

### 5.3 Cross-user transfer rule (critical)

Creator may include accounts owned by others **only if**:

1. `ledger_txn.household_id` is set.
2. Every non-owned account in splits is **shared** into that household.
3. Transaction is transfer-like: contains **only** account types in a safe allow-list.
    - v1 simplest: allow only `ASSET` accounts.
    - optional: allow `ASSET` + `LIABILITY` (paying someone else’s shared credit card), but keep strict initially.
4. Category tags should be **null** for pure transfers (or ignore if present).

**Recommended v1:** Only ASSET↔ASSET cross-user transfers.

---

## 6) API Surface (v1)

> Names are illustrative. Adapt to your framework conventions.

### 6.1 Currency

- `GET /currencies` (seed static list + minor units)

### 6.2 Accounts

- `POST /accounts`
- `GET /accounts?mode=individual|household&householdId=...`
- `PATCH /accounts/{id}` (rename)
- `POST /households/{id}/shares` (share account into household)
- `DELETE /households/{id}/shares/{accountId}` (unshare)

### 6.3 Categories (tags)

- `POST /categories`
- `GET /categories`
- `PATCH /categories/{id}` (rename)
- `DELETE /categories/{id}` (optional: soft-delete)

### 6.4 Household

- `POST /households`
- `GET /households`
- `GET /households/{id}`
- `POST /households/{id}/invite` (OWNER/ADMIN)
- `POST /households/{id}/accept-invite` (phase next; stub now)
- `PATCH /households/{id}/members/{userId}` (role changes OWNER/ADMIN only; optional later)

### 6.5 Ledger

- `POST /ledger/transactions` (create txn + splits)
- `GET /ledger/transactions?mode=...&dateFrom=...&dateTo=...&accountId=...&categoryId=...`
- `GET /ledger/accounts/{id}/balance?asOf=...`
- `GET /ledger/reports/monthly?mode=...&period=YYYY-MM`

---

## 7) DTOs & Examples (Agents copy/paste)

### 7.1 CreateTransactionRequest (recommended)

```json
{
  "mode": "HOUSEHOLD",
  "householdId": "uuid-or-null",
  "txnDate": "2026-02-15",
  "currency": "EUR",
  "description": "Electricity bill",
  "splits": [
    { "accountId": "checking-uuid", "side": "CREDIT", "amountMinor": 4500, "categoryTagId": null },
    { "accountId": "apartment-bills-expense-uuid", "side": "DEBIT", "amountMinor": 4500, "categoryTagId": "electricity-tag-uuid" }
  ]
}
```

> Note: we use DEBIT/CREDIT explicitly, plus positive amount_minor.

### 7.2 Example: Expense

Electricity 45.00 EUR from Checking:

- Checking (ASSET): CREDIT 4500
- Apartment Bills (EXPENSE): DEBIT 4500, categoryTag=Electricity

### 7.3 Example: Income

Salary 2000.00 EUR into Checking:

- Checking (ASSET): DEBIT 200000
- Salary (INCOME): CREDIT 200000

### 7.4 Example: Asset transfer (same owner)

Move 300 from Checking to Savings:

- Checking: CREDIT 30000
- Savings: DEBIT 30000

### 7.5 Example: Liability payment (own loan)

Pay Mortgage 500:

- Checking (ASSET): CREDIT 50000
- Mortgage (LIABILITY): DEBIT 50000  
  (credit-normal account decreases with DEBIT in reporting)

### 7.6 Example: Opening balance using Equity

Initial balance 1000 in Checking:

- Checking (ASSET): DEBIT 100000
- Opening Equity (EQUITY): CREDIT 100000

### 7.7 Example: Cross-user transfer in household

User X sends 50 EUR from X:Checking to Y:Savings; both accounts shared to household H.

- Txn created by X with `householdId=H`
- Splits:
    - X Checking: CREDIT 5000
    - Y Savings:  DEBIT 5000

Validator passes only if:

- both accounts are shared in H
- txn is “transfer-like” (ASSET only)

### 7.8 Cross-currency transfer (optional v1.5)

Because v1 enforces “txn currency == account currency”, cross-currency transfers should be deferred or modeled as:

- withdraw in one currency, deposit in another, without automatic linkage in v1
- or two transactions linked by `fx_group_id` after introducing per-currency FX clearing accounts

**Recommendation:** Defer FX until v2. For v1, only allow transfers between accounts with the same currency.

---

## 8) Service Layer: Validation & Algorithms

### 8.1 ValidateCreateTransaction(request, currentUserId)

1. Require `splits.size >= 2`.
2. Load accounts for all split.accountId.
3. Currency rule:
    - `request.currency` exists in `currency` table.
    - For each account: `account.currency == request.currency` (v1).
4. Double-entry:
    - `sumDebits = Σ amount_minor where side=DEBIT`
    - `sumCredits = Σ amount_minor where side=CREDIT`
    - Require `sumDebits == sumCredits`
5. Category rule:
    - if categoryTagId is present: tag.owner_user_id must be current user (v1).
6. Permission rule:
    - If all accounts are owned by current user → OK.
    - Else:
        - require request.mode == HOUSEHOLD and householdId != null
        - require current user is ACTIVE member of household
        - require every non-owned account is shared into household
        - require transfer-like constraints (v1: account.type == ASSET for all splits)
7. Persist:
    - Insert ledger_txn, then ledger_splits.
    - Set `ledger_txn.household_id` when mode=HOUSEHOLD.
8. Return transaction details (including derived type for UI if desired).

### 8.2 Derived UI transaction kind (optional)

Given split accounts:

- If all account types in {ASSET, LIABILITY, EQUITY} → TRANSFER_OR_BALANCE
- If any EXPENSE → EXPENSE
- If any INCOME → INCOME

Keep as derived metadata, not truth.

---

## 9) Reporting Queries (SQL patterns)

### 9.1 Account balance (as of date)

```sql
with s as (
  select a.id as account_id, a.type,
         sum(case when ls.side='DEBIT' then ls.amount_minor else 0 end) as debits,
         sum(case when ls.side='CREDIT' then ls.amount_minor else 0 end) as credits
  from account a
  join ledger_split ls on ls.account_id = a.id
  join ledger_txn lt on lt.id = ls.txn_id
  where a.id = :accountId
    and lt.txn_date <= :asOf
  group by a.id, a.type
)
select account_id,
       case when type in ('ASSET','EXPENSE') then (debits - credits)
            else (credits - debits)
       end as balance_minor
from s;
```

### 9.2 Monthly expense totals by expense account + category tag

```sql
select
  exp.id as expense_account_id,
  exp.name as expense_account_name,
  ct.id as category_id,
  ct.name as category_name,
  sum(case when ls.side='DEBIT' then ls.amount_minor else -ls.amount_minor end) as net_minor
from ledger_txn lt
join ledger_split ls on ls.txn_id = lt.id
join account exp on exp.id = ls.account_id
left join category_tag ct on ct.id = ls.category_tag_id
where exp.type = 'EXPENSE'
  and lt.txn_date >= :monthStart
  and lt.txn_date < :monthEnd
  and (
     (:mode = 'INDIVIDUAL' and exp.owner_user_id = :userId)
     or
     (:mode = 'HOUSEHOLD' and lt.household_id = :householdId)
  )
group by exp.id, exp.name, ct.id, ct.name
order by exp.name, ct.name;
```

---

## 10) Security & Access Control

### 10.1 Application-level authorization (v1)

- Account access if owned by user OR shared via household in household mode.
- Transaction access:
    - individual mode: affects owned accounts (recommended) OR created_by=user
    - household mode: `lt.household_id == householdId` AND user is ACTIVE member

### 10.2 Optional DB-level RLS (later)

Postgres RLS can enforce access; defer until v2.

---

## 11) Migration & Seeding

### 11.1 Seed currencies

Seed common currencies into `currency` table:

- EUR, USD, GBP, BGN, JPY, CHF, etc.

### 11.2 Optional default “Opening Equity”

On onboarding:

- Create an `EQUITY` account named “Opening Equity” per currency used (or create on demand).

---

## 12) Test Plan (must-have)

### 12.1 Unit tests (ledger validations)

- Reject txn with <2 splits
- Reject txn where debits != credits
- Reject txn where any split amount <= 0
- Reject txn where account currency != txn currency
- Reject posting to non-owned account in individual mode
- Reject cross-user txn if account not shared into household
- Reject cross-user txn if includes EXPENSE/INCOME types (v1 strict)

### 12.2 Integration tests (household overlay)

- Share account → appears in household mode for all members
- Unshare → disappears
- Cross-user transfer between shared accounts succeeds
- Attempt to post expense into spouse account fails

### 12.3 Reporting tests

- Balances match expected after sequence of txns
- Expense summaries correct per month

---

## 13) Work Split Suggestions (Sub-agent Task Breakdown)

### Agent A — DB & Migrations

- Implement migrations for all tables (currency, household, membership, account, share, category, ledger).
- Seed currencies.
- Add indexes.
- Optional: add constraints/checks.

### Agent B — Ledger Service + API

- Create account CRUD.
- Create category CRUD.
- Implement create transaction endpoint with all validations.
- Implement transaction listing filters.
- Implement balance query endpoint.

### Agent C — Household Service + API

- Household CRUD.
- Membership roles/permissions.
- Account sharing endpoints.
- Household-mode list endpoints.

### Agent D — Reporting & Aggregations

- Monthly summaries by expense account + category.
- Trend endpoints suitable for charts (timeseries: daily/weekly/monthly).
- Performance: indexes + query tuning.

### Agent E — Frontend (later step)

- Mode switch UI (individual/household).
- Chart.js integration consuming reporting endpoints.
- Transaction entry UX that generates splits automatically.

---

## 14) Implementation Order (Step-by-step Checklist)

### Phase 1 — Foundations (Ledger + Currency)

1. Add `currency` table and seed it.
2. Add `account` table with types incl. EQUITY.
3. Add `category_tag` table.
4. Add `ledger_txn` and `ledger_split`.
5. Implement Account CRUD (owned by user).
6. Implement Category CRUD (owned by user).
7. Implement Create Transaction (double-entry enforcement, currency enforcement, ownership rule).
8. Implement basic listing of transactions + splits.
9. Implement balance computation endpoint.

### Phase 2 — Household Overlay

10. Add `household` and `household_member` tables.
11. Implement household create/list/read.
12. Implement membership roles/permissions (OWNER/ADMIN can invite).
13. Add `household_account_share`.
14. Implement share/unshare account.
15. Implement household-mode account listing.
16. Implement household-mode transaction creation:
    - set txn.household_id
    - enforce cross-user transfer rule (shared ASSET accounts only)
17. Implement household-mode transaction listing (filter by household_id).

### Phase 3 — Reporting (for charts later)

18. Implement reporting endpoints:
    - monthly expense totals by expense account + tag
    - balances over time (timeseries)
19. Add caching/materialized views only if performance requires.

### Phase 4 — Invite acceptance (next step)

20. Implement invite flow:
    - OWNER/ADMIN can invite → member row status=INVITED
    - invitee accepts → status=ACTIVE, joined_at set

---

## 15) Notes & Best Practices (Do not skip)

- Avoid storing account balances; compute from splits.
- Keep ledger operations deterministic and validated.
- Avoid cross-currency in a single transaction in v1.
- Use UUIDs and enforce FK integrity.
- Prefer soft-delete/archival flags over hard delete for accounts.
- Consider “reversal transactions” later instead of mutation for auditability.

---

## Appendix A — Minimal helper to compute sign for UI (optional)

When displaying a transaction for a specific account:

- If account is debit-normal:
    - DEBIT increases balance, CREDIT decreases
- If account is credit-normal:
    - CREDIT increases balance, DEBIT decreases

Expose per split:

- `effect_minor` = +amount for increase, -amount for decrease (relative to that account)

---

## Appendix B — Example: User Experience mapping to splits (agent guidance)

When user enters:

- amount + fromAccount + toAccount (transfer)
- amount + fromAccount + expenseAccount + categoryTag (expense)
- amount + incomeAccount + toAccount (income)

The UI should generate splits automatically:

- Transfer: CREDIT from, DEBIT to
- Expense: CREDIT asset, DEBIT expense
- Income: DEBIT asset, CREDIT income

The backend still validates, so UI mistakes are caught.
