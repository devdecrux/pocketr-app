# Development Data Seed Plan (Liquibase)

This document defines the implementation plan for bootstrapping deterministic development data with Liquibase.

The target outcome is:

1. Liquibase owns database initialization.
2. Development seed data runs only in development mode.
3. The seed runs only on a fresh application database.
4. The dataset is deterministic and realistic enough for manual UI and API testing.

Implementation note:

1. Adopting Liquibase adds a new dependency and changes the DB initialization model.
2. Per `AGENTS.md`, that implementation step requires explicit user approval before code changes are made.

## 1) Goals

1. Seed useful local development data automatically for a fresh local database.
2. Keep the seed deterministic so every fresh database gets the same users, accounts, categories, household, and transaction history.
3. Ensure seed execution is limited to development mode.
4. Ensure seed execution is skipped when the application database already contains data.
5. Keep the dataset SQL-driven so it is easy to inspect, diff, and debug.

## 2) Why Liquibase

Liquibase is the preferred direction for this plan because it gives the repo all of the controls needed for this use case in one place:

1. `DATABASECHANGELOG` tracks whether a changeset already ran.
2. `contexts` can restrict seed changesets to development mode.
3. `preConditions` can check whether the application database is empty before the seed executes.
4. Formatted SQL changelogs let the runtime artifact stay SQL-first.

For this repo, that is a better fit than plain startup SQL because the runtime can decide both:

1. whether the environment is allowed to seed
2. whether the database is still eligible to seed

## 3) Prerequisite Decision

The Liquibase seed plan should be implemented with Liquibase as the authoritative database initializer.

Recommended rule:

1. Do not keep long-term dual ownership between Hibernate schema creation and Liquibase.
2. Move schema creation and reference data into Liquibase changesets.
3. After that, change Hibernate from `ddl-auto: update` to `ddl-auto: validate`.

Reason:

1. Liquibase runs before normal application startup logic.
2. If Hibernate is still responsible for creating the tables, Liquibase seed changesets would execute too early.
3. The clean model is:
   - Liquibase creates and updates schema
   - Liquibase seeds reference data
   - Liquibase seeds dev-only demo data through a gated changeset
   - Hibernate validates the schema only

## 4) Recommended Liquibase Structure

Add Liquibase in a way that separates common schema/reference data from dev-only seed data.

Suggested files:

- `pocketr-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- `pocketr-api/src/main/resources/db/changelog/001-schema.sql`
- `pocketr-api/src/main/resources/db/changelog/010-reference-currencies.sql`
- `pocketr-api/src/main/resources/db/changelog/900-dev-seed.sql`
- `pocketr-api/src/main/resources/application.yaml`
- `pocketr-api/src/main/resources/application-dev.yaml`

Suggested responsibility split:

1. `001-schema.sql`
   - Creates the current application schema that Hibernate currently derives.

2. `010-reference-currencies.sql`
   - Seeds the common currency catalog for all environments.
   - Replaces the current startup-only `CurrencySeeder` behavior.

3. `900-dev-seed.sql`
   - Inserts the 8 users, their accounts, categories, household, ledger history, and `account_current_balance`.
   - Runs only in dev through a Liquibase context.
   - Skips if the DB already contains application data.

## 5) Runtime Configuration

Use Liquibase contexts so dev data never runs outside development mode.

Recommended configuration:

1. In `application.yaml`:
   - enable Liquibase
   - point to the master changelog
   - set `spring.liquibase.contexts=app`
   - set `spring.jpa.hibernate.ddl-auto=validate`

2. In `application-dev.yaml`:
   - keep the same changelog
   - set `spring.liquibase.contexts=app,dev-seed`

3. Put the development seed changeset in `900-dev-seed.sql` with context `dev-seed`.

This gives the following behavior:

1. non-dev environments run only common schema/reference changesets
2. dev environments run common changesets plus the dev seed changeset

## 6) Dev-Seed Guard Logic

The development seed must not run just because the `dev` profile is active.

The `900-dev-seed.sql` changeset should include a Liquibase precondition:

1. `sqlCheck` against `users`
2. expected result: `0`
3. `onFail: MARK_RAN`
4. `onError: HALT`

Recommended meaning:

1. If `users` is empty, the seed is allowed to run.
2. If `users` already contains rows, Liquibase records the changeset as handled and skips the inserts.
3. If the SQL guard itself fails, startup should fail loudly.

This fits the desired behavior for a fresh database:

1. a brand-new DB gets seeded once
2. an already-used DB is not seeded later by accident

Operational caveat:

1. If someone manually deletes app data but leaves `DATABASECHANGELOG` intact, Liquibase will not automatically re-seed.
2. The supported reset path remains recreating the local database volume.

## 7) Current Model Constraints That The Seed Must Respect

1. Categories are user-owned. The same category set must be duplicated per seeded user.
2. The ledger is double-entry. Categories do not replace ledger accounts.
3. To represent incomes and expenses correctly, each user still needs operational `INCOME` and `EXPENSE` accounts.
4. Passwords in `users` must be stored as BCrypt hashes because the application uses `BCryptPasswordEncoder`.
5. `account_current_balance` is a derived read model. Raw ledger inserts are not enough on their own; the seed must rebuild that table.
6. Household roles are `OWNER`, `ADMIN`, and `MEMBER`.
7. Cross-user household posting is currently limited to `ASSET` accounts when multiple account owners appear in one transaction.
8. To keep the chart of accounts clean, the seed should not rely on opening-balance flows that would create `Opening Equity`.

## 8) Seeded Users

Create these 8 users in this exact order:

| # | First Name | Last Name | Email | Password |
|---|---|---|---|---|
| 1 | Alex | Turner | `alex.1001@test.com` | `test1` |
| 2 | Nina | Petrova | `nina.1002@test.com` | `test2` |
| 3 | Martin | Cole | `martin.1003@test.com` | `test3` |
| 4 | Elena | Ivanova | `elena.1004@test.com` | `test4` |
| 5 | Victor | Reed | `victor.1005@test.com` | `test5` |
| 6 | Sophie | Marin | `sophie.1006@test.com` | `test6` |
| 7 | David | Stone | `david.1007@test.com` | `test7` |
| 8 | Lara | Miles | `lara.1008@test.com` | `test8` |

Implementation rule:

1. Precompute and commit BCrypt hashes for `test1` through `test8`.
2. Insert the hashes directly in the Liquibase seed SQL.

## 9) Categories

Before any transactions are inserted for a user, insert the same category catalog for that user.

Recommended category set:

- `Salary`
- `Freelance`
- `Interest`
- `Cashback`
- `Tax Refund`
- `Groceries`
- `Dining Out`
- `Transport`
- `Fuel`
- `Utilities`
- `Internet`
- `Phone`
- `Subscriptions`
- `Healthcare`
- `Shopping`
- `Entertainment`
- `Travel`
- `Home Maintenance`
- `Insurance`
- `Gifts`
- `Mortgage Payment`
- `Car Lease Payment`
- `Credit Card Payment`

Rules:

1. Every category row must reference the correct `owner_user_id`.
2. Expense and income transactions should always carry a category.
3. Pure asset-to-asset transfers may remain uncategorized.
4. Liability payment transactions should use the matching debt category on the liability split.

## 10) Accounts Per User

For each user, create these `EUR` accounts:

Requested user-facing accounts:

1. `Daily Checking` (`ASSET`)
2. `Bills Checking` (`ASSET`)
3. `Emergency Savings` (`ASSET`)
4. one liability account from the deterministic rotation below (`LIABILITY`)

Operational accounts required by the current model:

5. `Income` (`INCOME`)
6. `Expenses` (`EXPENSE`)

Liability rotation:

| User Index | Liability Account Name |
|---|---|
| 1 | `Apartment Mortgage` |
| 2 | `Car Leasing` |
| 3 | `Credit Card Repayment` |
| 4 | `Apartment Mortgage` |
| 5 | `Car Leasing` |
| 6 | `Credit Card Repayment` |
| 7 | `Apartment Mortgage` |
| 8 | `Car Leasing` |

Implementation rule:

1. Use fixed UUIDs for all accounts.
2. Keep those UUIDs stable so later `ledger_split`, `household_account_share`, and `account_current_balance` inserts are readable and deterministic.

## 11) Transaction Volume And Shape

Per user, insert this minimum set:

1. `6` income transactions
   - `2` per asset account
2. `30` expense transactions
   - `10` per asset account
3. `6` asset transfer transactions
   - enough so each asset account participates in at least `2`
4. `3` liability transactions
   - `1` liability increase
   - `2` liability payments

That produces `45` individual transactions per user.

Across 8 users, that yields `360` individual transactions before household-specific activity.

## 12) Transaction Templates

Use deterministic dates over roughly the last 120 days.

Liquibase SQL can keep the data recent by using fixed offsets from `CURRENT_DATE`, for example:

1. `CURRENT_DATE - INTERVAL '118 days'`
2. `CURRENT_DATE - INTERVAL '96 days'`
3. `CURRENT_DATE - INTERVAL '12 days'`

The offsets must be fixed and stable in the SQL file.

### 12.1 Daily Checking

Income examples:

- monthly salary
- freelance payment

Expense examples:

- groceries
- dining out
- transport pass
- fuel top-up
- pharmacy
- clothes
- coffee
- entertainment
- gifts
- small shopping

Transfer examples:

- transfer to savings
- transfer in from bills checking

### 12.2 Bills Checking

Income examples:

- cashback
- tax refund

Expense examples:

- utilities
- internet
- phone
- subscriptions
- insurance
- home supplies
- parking
- car service
- streaming
- school or course fee

Transfer examples:

- transfer to daily checking
- transfer in from savings

### 12.3 Emergency Savings

Income examples:

- interest
- bonus deposit

Expense examples:

- vacation booking
- laptop replacement
- appliance repair
- dental bill
- emergency medical expense
- holiday gifts
- travel deposit
- home repair
- furniture
- annual insurance top-up

Transfer examples:

- transfer in from daily checking
- transfer out to bills checking

### 12.4 Liability Activity

Each user’s liability account should have a believable remaining balance, not a huge origination amount.

Recommended pattern:

1. one liability increase transaction
   - `Daily Checking` `DEBIT`
   - liability account `CREDIT`
2. two liability payment transactions
   - `Daily Checking` `CREDIT`
   - liability account `DEBIT`
3. the liability split on each payment carries the matching debt category

Suggested relative scale:

- `Apartment Mortgage`: medium balance
- `Car Leasing`: smaller balance
- `Credit Card Repayment`: small balance

## 13) Household Scenario

Create one household using the first three seeded users:

1. `alex.1001@test.com` creates the household and is the `OWNER`
2. `nina.1002@test.com` joins as `MEMBER`
3. `martin.1003@test.com` joins as `MEMBER`

Recommended household name:

- `Test Household Alpha`

Share into the household:

1. Alex `Daily Checking`
2. Nina `Daily Checking`
3. Martin `Daily Checking`

Seed `6` additional household-visible transactions after sharing:

1. Alex creates `2` household-mode expense transactions using Alex-owned accounts
2. Nina creates `2` household-mode expense transactions using Nina-owned accounts
3. Martin creates:
   - `1` household-mode expense transaction using Martin-owned accounts
   - `1` cross-user reimbursement transfer between shared checking accounts

Rules:

1. Household-mode expense transactions must use accounts owned by the transaction creator.
2. Only the reimbursement transaction should cross owners.
3. That cross-user reimbursement transaction must use `ASSET` accounts only.
4. Category tags used by a transaction must belong to the creator of that transaction.

## 14) Recommended Changeset Design

Use a master changelog with separate common and dev-only changesets.

Recommended sequence:

1. `001-schema.sql`
2. `010-reference-currencies.sql`
3. `900-dev-seed.sql`

Recommended Liquibase shape for the dev seed:

1. formatted SQL changeset
2. context `dev-seed`
3. precondition `sqlCheck expectedResult:0 SELECT COUNT(*) FROM users`
4. `onFail: MARK_RAN`
5. `runInTransaction:true`

Recommended internal order inside `900-dev-seed.sql`:

1. insert users
2. insert `user_roles`
3. insert categories
4. insert accounts
5. insert household, members, and shares
6. insert `ledger_txn`
7. insert `ledger_split`
8. rebuild `account_current_balance`
9. reset identity sequences for explicit numeric IDs

## 15) Current Balance Snapshot Rebuild

Because the seed inserts ledger rows directly, it must rebuild `account_current_balance` at the end.

Use an upsert query equivalent to:

1. aggregate `SUM(DEBIT) - SUM(CREDIT)` per `account_id` from `ledger_split`
2. insert those totals into `account_current_balance`
3. `ON CONFLICT (account_id) DO UPDATE`

The seed is not complete unless current-balance reads work immediately after startup.

## 16) Determinism Rules

1. Use fixed numeric IDs for `users` and `user_roles` where practical.
2. Use fixed UUIDs for categories, accounts, transactions, splits, household rows, and shares.
3. Use stable insert order.
4. Use fixed date offsets from `CURRENT_DATE`.
5. Use stable descriptions and amounts; do not rely on runtime randomness.
6. Reset identity sequences after explicit numeric inserts so later application-created rows continue cleanly.

## 17) Validation And Acceptance Criteria

The Liquibase-based implementation is complete when all of the following are true:

1. Outside the `dev` profile, the dev-seed changeset does not run.
2. In the `dev` profile with an empty DB, startup creates exactly:
   - `8` users
   - `48` accounts total
   - `1` household
   - `3` active household members
   - `3` household account shares
   - `360` individual transactions
   - `6` additional household-visible transactions
   - `366` total `ledger_txn` rows
3. Every seeded user can authenticate with the expected password:
   - user 1 -> `test1`
   - user 2 -> `test2`
   - ...
   - user 8 -> `test8`
4. Every seeded user has:
   - `3` asset accounts
   - `1` liability account
   - `1` income account
   - `1` expense account
5. Every asset account participates in at least:
   - `2` income transactions
   - `10` expense transactions
   - `2` transfer transactions
6. Every income and expense transaction has a category owned by the transaction creator.
7. Liability payment transactions use the correct debt category.
8. `account_current_balance` matches the ledger-derived raw balances after the seed finishes.
9. Starting the app again in `dev` on the already-seeded DB does not duplicate rows.
10. `DATABASECHANGELOG` contains the dev-seed changeset only once for that database.

## 18) Testing Plan

Add integration-style backend tests around Liquibase startup behavior.

Recommended test coverage:

1. `should not run dev seed outside dev profile`
2. `should run dev seed on fresh dev database`
3. `should skip dev seed on non-empty dev database`
4. `should create BCrypt passwords matching test1..test8`
5. `should create required account counts per user`
6. `should satisfy minimum transaction counts per asset account`
7. `should create household membership and shares correctly`
8. `should keep category ownership aligned with the transaction creator`
9. `should rebuild account_current_balance from seeded ledger rows`
10. `should not duplicate seed rows on second startup`

## 19) Reset Strategy

Supported reset path for local development:

1. remove the local DB volume
2. start the DB again
3. start the app in `dev`
4. let Liquibase rebuild schema and reapply the dev seed

Not recommended:

1. manually truncating application tables while keeping `DATABASECHANGELOG`
2. mixing Liquibase ownership with long-term Hibernate `ddl-auto:update`
