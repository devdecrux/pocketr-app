# Currency Exchange - Implementation Plan

This document describes the incremental implementation of externally synchronized currencies, current exchange rates, and currency conversion in the Pocketr ledger.

The work is organized so independent packages can be prepared concurrently by subagents in isolated worktrees. Integration into `feat/currency_exchange` remains sequential.

After each integrated commit:

1. Run the targeted tests and formatting checks.
2. Stop for review and approval.
3. Do not push without explicit permission.

## 1. Goals

1. Load the supported currency catalog from Frankfurter.
2. Refresh currencies and current exchange rates every 24 hours.
3. Store only the latest rates for one configurable app-wide base currency.
4. Convert every ledger split into its account currency.
5. Store the exchange rate applied to each split.
6. Make synchronized currencies available in the existing account currency dropdowns.

## 2. Non-goals

1. Keeping historical exchange-rate rows.
2. Fetching rates while creating a transaction.
3. Adding exchange-rate management UI.
4. Restricting conversion to specific account types.
5. Changing existing ownership, household, or account-type authorization rules.
6. Reading the base currency from `app_settings` in this iteration. For the time being default should be EUR

## 3. External API

Use Frankfurter v2:

- Currencies: `GET https://api.frankfurter.dev/v2/currencies`
- Rates: `GET https://api.frankfurter.dev/v2/rates?base={baseCurrency}`

Expected currency fields:

- `iso_code`
- `iso_numeric`
- `name`
- `symbol`

Expected rate fields:

- `date`
- `base`
- `quote`
- `rate`

Use Spring's existing HTTP client support. Do not add a dependency.

## 4. Data Model

### 4.1 `currencies`

Rename the existing `currency` table to `currencies` and update the JPA model.

Required columns:

| Column | Type | Meaning |
| --- | --- | --- |
| `code` | `VARCHAR(3)` | ISO alphabetic code and primary key |
| `iso_numeric` | `VARCHAR(3)` | ISO numeric code |
| `name` | `VARCHAR` | Display name |
| `symbol` | `VARCHAR` | Display symbol |
| `minor_unit` | `SMALLINT` | Number of fractional digits |

Frankfurter does not provide `minor_unit`. Resolve it with `java.util.Currency.defaultFractionDigits`. Use `2` only when JVM metadata is unavailable or invalid.

Do not delete currencies during synchronization. Accounts, transactions, settings, or rates may already reference them.

### 4.2 `currencies_exchange_rates`

Store one current row per base/quote pair.

| Column | Type | Meaning |
| --- | --- | --- |
| `base_currency` | `VARCHAR(3)` | Configured rate base |
| `quote_currency` | `VARCHAR(3)` | Currency produced by the rate |
| `rate` | `NUMERIC(38,18)` | Units of quote currency for one base unit |
| `provider_date` | `DATE` | Date returned by Frankfurter |
| `updated_at` | `TIMESTAMPTZ` | Local synchronization timestamp |

Rules:

- Primary key: `base_currency`, `quote_currency`.
- Both currencies reference `currencies(code)`.
- `rate` must be greater than zero.
- A successful refresh replaces the current set for the configured base.
- A failed or empty refresh keeps the previous rows unchanged.
- The base-to-base rate is treated as `1` in code and does not require a row.

### 4.3 `ledger_split`

Add:

| Column | Type | Meaning |
| --- | --- | --- |
| `exchange_rate` | `NUMERIC(38,18)` | Transaction currency to account currency rate used by the split |

Split semantics after conversion:

- `ledger_txn.currency` is the transaction/input currency.
- Incoming `CreateSplitDto.amountMinor` is expressed in the transaction currency.
- Persisted `ledger_split.amount_minor` is expressed in the split account's currency.
- `ledger_split.exchange_rate` converts one major unit of transaction currency into the account currency.
- Same-currency splits use rate `1`.

Example:

- Transaction currency: `EUR`
- Credit EUR account: `10.00 EUR`, rate `1`
- Debit CZK account: converted CZK amount, rate `EUR -> CZK`

The request remains balanced before conversion in the transaction currency. Account balances use the converted persisted split amounts.

## 5. Rate Resolution

The database stores rates relative to the configured app base, initially `EUR`.

Resolve a direct transaction-to-account rate as follows:

```text
transaction == account:
    1

transaction == configured base:
    base -> account

account == configured base:
    1 / (base -> transaction)

otherwise:
    (base -> account) / (base -> transaction)
```

Use `BigDecimal` throughout rate calculation. Do not use `Double`.

Convert through major units so currencies with different minor units are handled correctly:

```text
transaction major amount
    = transaction minor amount / 10 ^ transaction minor unit

account major amount
    = transaction major amount * exchange rate

account minor amount
    = account major amount * 10 ^ account minor unit
```

Round the final account minor amount to a whole number with `HALF_UP`.

## 6. Scheduling and Failure Handling

Add two focused synchronizers and one small scheduler/orchestrator:

1. Synchronize currencies.
2. Synchronize exchange rates for a supplied base currency.

Run synchronization:

- once during application startup
- every 24 hours afterward

Use an application property for the initial base currency:

```yaml
pocketr:
  currency:
    base: EUR
```

The synchronization and conversion APIs must accept the base currency as a parameter so configuration can later come from `app_settings`.

Failure rules:

- Log failures without removing existing currencies or rates.
- Never partially replace the current rates.
- Transaction creation must fail with a clear bad-request error when a required database rate is unavailable.
- Transaction creation must never call Frankfurter directly.

## 7. Parallel Delivery Rules

### 7.1 Coordinator Responsibilities

The main agent owns:

1. Dependency ordering.
2. Shared contract decisions.
3. Integration into `feat/currency_exchange`.
4. Full diff review.
5. Final verification.
6. Stopping after each integrated commit for user review.

### 7.2 Subagent Rules

Each subagent must:

1. Work from the same reviewed base commit in an isolated worktree or branch.
2. Own exactly one work package at a time.
3. Edit only the files assigned to that package.
4. Avoid broad formatting or unrelated refactoring.
5. Add and run the tests assigned to the package.
6. Produce one focused Conventional Commit.
7. Never push.
8. Report changed files, tests run, and assumptions to the coordinator.

If a required shared contract is unclear, the subagent must stop instead of inventing a competing model.

### 7.3 Shared Contracts

These contracts must be treated as fixed before parallel implementation begins:

- Table names: `currencies`, `currencies_exchange_rates`.
- Rate precision: `NUMERIC(38,18)` and `BigDecimal`.
- Rate direction: transaction currency to split account currency.
- Request split amounts: transaction currency minor units.
- Persisted split amounts: account currency minor units.
- Split DTO field names: `accountCurrency` and `exchangeRate`.
- Same-currency rate: `1`.
- Initial app base property: `pocketr.currency.base`, default `EUR`.
- Frankfurter base URL property: `pocketr.currency.frankfurter.base-url`.
- Missing required rate: reject the transaction without partial writes.

Only the coordinator may change these contracts after implementation starts.

### 7.4 Shared-File Ownership

Files likely to cause conflicts must have one owner per wave:

- `db/migration/V1__init.sql`: schema owner only.
- `Currency.kt`, `CurrencyDto.kt`, `CurrencyController.kt`: currency model owner only.
- `application.yaml`: HTTP client owner in Wave 1, scheduler owner afterward.
- `ManageLedgerImpl.kt`: ledger snapshot owner first, ledger conversion owner afterward.
- Frontend transaction files: UI owner only after the backend response contract is integrated.

Subagents must not edit this plan while implementation is in progress.

## 8. Work Packages and Incremental Commits

### WP1 / Commit 1: Align Currency Schema

Suggested commit:

```text
refactor(currency): align currency persistence model
```

Changes:

1. Rename `currency` to `currencies`.
2. Add `iso_numeric` and `symbol`.
3. Update all foreign keys.
4. Update `Currency`, `CurrencyDto`, seed data, and persistence tests.
5. Keep existing API behavior compatible while exposing the additional fields.

Verification:

- Currency repository tests.
- App settings repository tests.
- Backend formatting.

Owned files:

- `pocketr-api/src/main/resources/db/migration/V1__init.sql`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/entities/db/ledger/Currency.kt`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/entities/dtos/CurrencyDto.kt`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/controllers/CurrencyController.kt`
- Directly affected currency and app-settings tests

Dependencies: none.

### WP2 / Commit 2: Add Current Exchange-Rate Schema

Suggested commit:

```text
feat(currency): add current exchange rate model
```

Changes:

1. Create `currencies_exchange_rates`.
2. Add exchange-rate entity and repository.
3. Add persistence tests for uniqueness, foreign keys, and positive rates.

Verification:

- Targeted repository tests.
- Backend formatting.

Owned files:

- `pocketr-api/src/main/resources/db/migration/V1__init.sql`
- New exchange-rate entity, identifier, and repository files
- New exchange-rate repository tests

Dependencies: WP1.

### WP3 / Commit 3: Add Frankfurter HTTP Client

Suggested commit:

```text
feat(currency): add Frankfurter client
```

Changes:

1. Add configurable Frankfurter base URL.
2. Implement currency and rate endpoints.
3. Add response DTOs.
4. Add client contract tests for successful and invalid responses.

Verification:

- Targeted HTTP client tests.
- Backend formatting.

Owned files:

- New files under a dedicated `services/currency/frankfurter` package
- Frankfurter-specific response DTOs in the same package
- Frankfurter client tests
- `application.yaml` for the provider base URL only

Dependencies: none for preparation; integrate after WP2.

The client work must not depend on JPA entities or repositories.

### WP4 / Commit 4: Synchronize Currency Catalog

Suggested commit:

```text
feat(currency): synchronize currency catalog
```

Changes:

1. Upsert Frankfurter currencies.
2. Derive minor units from JVM currency metadata.
3. Preserve currencies missing from later responses.
4. Keep minimal static seed data as an offline startup fallback.

Verification:

- Synchronization service tests.
- Existing currency endpoint tests or focused controller coverage.
- Backend formatting.

The frontend dropdowns already load `/api/v1/currencies`; no UI redesign is required.

Owned files:

- New currency catalog synchronizer and tests
- `CurrencySeeder.kt`
- Focused currency endpoint tests if required

Dependencies: WP1 and WP3.

### WP5 / Commit 5: Synchronize Current Rates

Suggested commit:

```text
feat(currency): synchronize current exchange rates
```

Changes:

1. Fetch rates for a supplied base currency.
2. Atomically replace current rows for that base.
3. Preserve old rows on failed or empty responses.

Verification:

- Rate synchronization tests.
- Backend formatting.

Owned files:

- New exchange-rate synchronizer and tests
- Exchange-rate repository additions needed for atomic replacement

Dependencies: WP1, WP2, and WP3.

### WP6 / Commit 6: Schedule Currency Data Refresh

Suggested commit:

```text
feat(currency): schedule currency data refresh
```

Changes:

1. Invoke both synchronizers once during application startup.
2. Invoke both synchronizers every 24 hours.
3. Configure `EUR` as the initial app base.
4. Keep catalog and rate failure handling independent.

Verification:

- Startup delegation test.
- Scheduler delegation test.
- Backend formatting.

Owned files:

- New scheduler/orchestrator and tests
- Scheduling enablement
- `application.yaml` for the app base and interval properties

Dependencies: WP4 and WP5.

### WP7 / Commit 7: Add Database-Backed Conversion

Suggested commit:

```text
feat(currency): add database-backed conversion
```

Changes:

1. Add a conversion service using only persisted rates.
2. Support direct, inverse, and cross-rate calculations.
3. Support different source and target minor units.
4. Add explicit missing-rate errors.

Verification:

- Same-currency conversion test.
- Direct-rate test.
- Inverse-rate test.
- Cross-rate test.
- Different-minor-unit and rounding tests.
- Missing-rate test.

Owned files:

- New currency conversion service and tests
- Read-only exchange-rate repository methods if required

Dependencies: WP1 and WP2.

The conversion package must not edit ledger entities or `ManageLedgerImpl`.

### WP8 / Commit 8: Snapshot Rates on Ledger Splits

Suggested commit:

```text
feat(ledger): snapshot split exchange rates
```

Changes:

1. Add `exchange_rate` to `ledger_split`.
2. Add the field to `LedgerSplit`.
3. Add `exchangeRate` and `accountCurrency` to `SplitDto`.
4. Store rate `1` for all existing same-currency transaction paths.
5. Keep current behavior unchanged before enabling mixed-currency posting.

Verification:

- Ledger persistence and DTO tests.
- Existing transaction tests.
- Backend formatting.

Owned files:

- `pocketr-api/src/main/resources/db/migration/V1__init.sql`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/entities/db/ledger/LedgerSplit.kt`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/entities/dtos/SplitDto.kt`
- DTO mapping section of `ManageLedgerImpl.kt`
- Directly affected ledger tests

Dependencies: WP1 and WP2.

### WP9 / Commit 9: Convert All Ledger Splits

Suggested commit:

```text
feat(ledger): support cross-currency splits
```

Changes:

1. Validate request balancing before conversion.
2. Resolve each split account's currency.
3. Convert and persist each split amount in its account currency.
4. Store each applied exchange rate.
5. Remove the requirement that every account matches the transaction currency.
6. Apply the behavior uniformly to `ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, and `EQUITY`.
7. Keep ownership, household, positive amount, split count, and side validations.
8. Ensure current-balance updates and transaction deletion use persisted account-currency amounts.

Verification:

- Same-currency regression tests.
- Cross-currency expense, income, transfer, debt payment, and equity/opening-entry tests.
- Current-balance creation and deletion tests.
- Missing-rate rollback test.
- Backend formatting.

Owned files:

- `ManageLedgerImpl.kt`
- `TransactionAccountCurrencyValidator.kt` and its tests
- Cross-currency ledger service and integration tests

Dependencies: WP7 and WP8.

### WP10 / Commit 10: Enable Cross-Currency Selection in the UI

Suggested commit:

```text
feat(ui): enable cross-currency transactions
```

Changes:

1. Remove destination currency filtering where it prevents selecting a valid account.
2. Consume `accountCurrency` and `exchangeRate` from the split response contract.
3. Format each persisted split with its account currency.
4. Keep the entered amount denominated in the transaction/source currency.
5. Continue using the synchronized currency endpoint for account dropdowns.

Verification:

- Transaction strategy unit tests.
- Transaction page unit tests.
- Frontend type check and lint.

Owned files:

- `pocketr-ui/src/types/ledger.ts`
- `pocketr-ui/src/views/TransactionsPage.vue`
- `pocketr-ui/src/utils/txnStrategies.ts` only if request construction changes
- Directly affected frontend tests

Dependencies: WP8 response contract and WP9 backend behavior.

## 9. Parallel Execution Waves

Parallel preparation is allowed only within a wave. Integration remains one commit at a time.

### Wave 1: Foundations

Run concurrently:

- Schema agent: WP1, then WP2 sequentially.
- HTTP agent: WP3.

Integration order:

1. WP1
2. WP2
3. WP3

Gate: currency schema, exchange-rate schema, and Frankfurter contracts compile together.

### Wave 2: Independent Backend Features

After Wave 1 is integrated, run concurrently:

- Catalog agent: WP4.
- Rate synchronization agent: WP5.
- Conversion agent: WP7.
- Ledger schema/DTO agent: WP8.

Integration order:

1. WP4
2. WP5
3. WP7
4. WP8

Gate: synchronization services, conversion service, and split DTO contract pass targeted tests.

### Wave 3: Orchestration and Ledger Integration

After Wave 2 is integrated, run concurrently:

- Scheduler agent: WP6.
- Ledger integration agent: WP9.
- UI agent: prepare WP10 against the integrated WP8 API contract.

Integration order:

1. WP6
2. WP9
3. WP10

The UI commit must not be integrated before WP9.

Gate: backend build, frontend checks, and same-currency regression tests pass.

### Dependency Graph

```text
WP1 -> WP2
WP1 -> WP4
WP3 -> WP4
WP1 -> WP5
WP2 -> WP5
WP3 -> WP5
WP4 -> WP6
WP5 -> WP6
WP1 -> WP7
WP2 -> WP7
WP1 -> WP8
WP2 -> WP8
WP7 -> WP9
WP8 -> WP9
WP8 -> WP10
WP9 -> WP10
```

## 10. Integration and Review Gates

For every prepared work package:

1. Rebase or recreate it from the latest reviewed integration commit.
2. Review the diff for file ownership violations.
3. Run the package's targeted tests.
4. Integrate exactly one commit.
5. Run `git diff --check` and the relevant formatter.
6. Present the commit and verification result to the user.
7. Wait for approval before integrating the next commit.

At the end of each wave, run:

- Backend: targeted tests for all packages in the wave, then `./gradlew build`.
- Frontend, when touched: `npm run type-check`, `npm run lint`, and targeted unit tests.

No subagent or coordinator may push without explicit user permission.

## 11. Compatibility Rules

1. Existing same-currency transactions must behave exactly as before.
2. Existing ledger rows should use an exchange rate of `1` when the schema is rebuilt or seeded.
3. Currency synchronization must not invalidate referenced currencies.
4. Rate refresh failure must not block unrelated same-currency transactions.
5. Cross-currency posting must fail atomically when conversion cannot be completed.
6. No account type receives special currency-conversion restrictions.

## 12. Future Work

After the above commits are complete and approved:

1. Read the base currency from `app_settings`.
2. Add an app-settings API and first-run configuration flow.
3. Convert dashboard and reporting totals into the configured base currency if desired.
4. Add manual rate refresh or synchronization status only if operationally necessary.
