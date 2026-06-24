# Currency Exchange Implementation Plan

This document defines the desired currency exchange behavior for Pocketr and the remaining work needed to finish the implementation.

## 1. Goals

1. Load the supported currency catalog from Frankfurter.
2. Refresh currencies and current exchange rates every 24 hours.
3. Store only the latest exchange rates for one configurable app-wide base currency.
4. Convert every ledger split into the split account's currency.
5. Store the exchange rate applied to each persisted split.
6. Make synchronized currencies available in existing account currency dropdowns.
7. Preserve existing same-currency transaction behavior.

## 2. Non-Goals

1. Keeping historical exchange-rate rows.
2. Fetching rates while creating a transaction.
3. Adding exchange-rate management UI.
4. Restricting conversion to specific account types.
5. Changing existing ownership, household, or account-type authorization rules.
6. Reading the base currency from `app_settings` in this iteration.
7. Reintroducing development demo data seeding. The old development seed service is intentionally removed.

## 3. Fixed Contracts

These contracts are the source of truth for backend, frontend, and tests.

| Area | Contract |
| --- | --- |
| Currency table | `currencies` |
| Rate table | `currencies_exchange_rates` |
| Rate precision | `NUMERIC(38,18)` persisted as `BigDecimal` |
| Rate direction | Transaction currency to split account currency |
| Request split amount | Transaction currency minor units |
| Persisted split amount | Split account currency minor units |
| Split DTO fields | `accountCurrency`, `exchangeRate` |
| Same-currency rate | `1` |
| Initial app base | `pocketr.currency.base`, default `EUR` |
| Frankfurter base URL | `pocketr.currency.frankfurter.base-url` |
| Missing required rate | Reject transaction without partial writes |

## 4. External API

Use Frankfurter v2 through Spring's existing HTTP client support. Do not add dependencies.

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

The Frankfurter client must remain independent from JPA entities and repositories.

## 5. Data Model

### 5.1 `currencies`

Rename the previous `currency` table to `currencies` and update the JPA model.

| Column | Type | Meaning |
| --- | --- | --- |
| `code` | `VARCHAR(3)` | ISO alphabetic code and primary key |
| `iso_numeric` | `VARCHAR(3)` | ISO numeric code |
| `name` | `VARCHAR` | Display name |
| `symbol` | `VARCHAR` | Display symbol |
| `minor_unit` | `SMALLINT` | Number of fractional digits |

Frankfurter does not provide `minor_unit`. Resolve it with `java.util.Currency.defaultFractionDigits`. Use `2` only when JVM metadata is unavailable or invalid.

Synchronization must upsert currencies and never delete currencies. Accounts, transactions, settings, or rates may already reference them.

### 5.2 `currencies_exchange_rates`

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
- A successful refresh replaces the current set for the supplied base.
- Failed or empty refresh keeps previous rows unchanged.
- Base-to-base rate is treated as `1` in code and does not require a row.

### 5.3 `ledger_split`

Add:

| Column | Type | Meaning |
| --- | --- | --- |
| `exchange_rate` | `NUMERIC(38,18)` | Transaction currency to account currency rate used by the split |

Split semantics:

- `ledger_txn.currency` is the transaction/input currency.
- Incoming `CreateSplitDto.amountMinor` is expressed in the transaction currency.
- Persisted `ledger_split.amount_minor` is expressed in the split account's currency.
- `ledger_split.exchange_rate` converts one major unit of transaction currency into the account currency.
- Same-currency splits use rate `1`.
- Existing same-currency rows should use `exchange_rate = 1` when the schema is rebuilt or seeded.

Example:

- Transaction currency: `EUR`
- Credit EUR account: `10.00 EUR`, rate `1`
- Debit CZK account: converted CZK amount, rate `EUR -> CZK`

The request remains balanced before conversion in the transaction currency. Account balances use converted persisted split amounts.

## 6. Synchronization

Add two focused synchronizers and one small scheduler/orchestrator:

1. Currency catalog synchronizer.
2. Exchange-rate synchronizer for a supplied base currency.
3. Currency data refresh scheduler.

Run synchronization:

- once during application startup
- every 24 hours afterward

Use configuration:

```yaml
pocketr:
  currency:
    base: EUR
    refresh-interval-ms: 86400000
    frankfurter:
      base-url: https://api.frankfurter.dev
```

Failure rules:

- Log failures without removing existing currencies or rates.
- Keep catalog and rate failures independent.
- Never partially replace the current rates.
- Transaction creation must fail with a clear bad-request error when a required database rate is unavailable.
- Transaction creation must never call Frankfurter directly.

## 7. Conversion

The database stores rates relative to the configured app base, initially `EUR`.

Resolve transaction-to-account rate:

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

## 8. Ledger Behavior

Transaction creation must:

1. Validate split count, positive amounts, split sides, account access, household access, and request balance before conversion.
2. Resolve the transaction currency from `currencies`.
3. Resolve each split account's currency.
4. Convert and persist each split amount in the account currency.
5. Store each applied exchange rate on the split.
6. Apply this uniformly to `ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, and `EQUITY`.
7. Update current balances using persisted account-currency split amounts.
8. Delete transactions by reversing persisted account-currency split amounts.
9. Roll back atomically when conversion cannot be completed.

The old rule requiring every account currency to match the transaction currency is obsolete and should be removed, including stale validator tests.

## 9. Frontend Behavior

The transaction UI must:

1. Remove destination account currency filtering where it prevents selecting a valid account.
2. Keep entered amounts denominated in the transaction/source currency.
3. Continue sending request split amounts in transaction/source currency minor units.
4. Consume `accountCurrency` and `exchangeRate` from split responses.
5. Format each persisted split with its account currency.
6. Continue using `/api/v1/currencies` for account currency dropdowns.

No exchange-rate management UI is required.

## 10. Required Verification

### 10.1 Backend Tests

Currency persistence:

- `currencies` table mapping.
- Foreign keys from accounts and transactions to `currencies(code)`.
- `iso_numeric`, `symbol`, and `minor_unit` persistence.

Exchange-rate persistence:

- Composite primary key uniqueness.
- Foreign keys to `currencies(code)`.
- Positive-rate constraint.

Frankfurter client:

- Successful currency response mapping.
- Successful rate response mapping.
- Invalid or missing required fields fail clearly.
- Configurable provider base URL is used.

Currency synchronization:

- Upserts currencies.
- Derives minor units from JVM metadata.
- Falls back to minor unit `2` when metadata is unavailable or invalid.
- Preserves currencies missing from later provider responses.
- Empty provider response keeps existing catalog unchanged.

Rate synchronization:

- Fetches rates for supplied base currency.
- Replaces current rows for that base on success.
- Preserves existing rows on failed or empty responses.
- Does not partially replace rows when a quote currency is missing.

Scheduler:

- Invokes catalog and rate synchronizers at startup.
- Invokes both synchronizers on the scheduled interval.
- Handles catalog and rate failures independently.
- Uses configured app base currency.

Conversion service:

- Same-currency conversion.
- Direct base-to-quote conversion.
- Inverse quote-to-base conversion.
- Cross-rate conversion between two quote currencies.
- Different source/target minor units.
- `HALF_UP` final minor-unit rounding.
- Missing-rate bad-request error.

Ledger integration:

- Same-currency regression.
- Cross-currency expense.
- Cross-currency income.
- Cross-currency transfer.
- Cross-currency debt payment.
- Cross-currency equity/opening-entry.
- Current-balance creation uses persisted account-currency amounts.
- Transaction deletion reverses persisted account-currency amounts.
- Missing-rate rollback leaves no partial transaction/split/balance writes.
- Ownership, household, split count, positive amount, and side validations still apply.

### 10.2 Frontend Tests

- Transaction strategy unit tests continue sending source-currency amounts.
- Transaction page tests cover mixed-currency account selection.
- Transaction page tests display split amounts with `accountCurrency`.
- Response types include `accountCurrency` and `exchangeRate`.

### 10.3 Commands

Backend:

```bash
cd pocketr-api
./gradlew test
./gradlew build
```

Frontend:

```bash
cd pocketr-ui
npm run type-check
npm run lint
npm run test:unit
```

Formatting:

```bash
git diff --check
```

## 11. Improvement Strategy

Use tests to protect behavior before simplifying implementation. Do not start with broad refactoring.

Recommended order:

1. Remove dead code and stale tests that encode old invariants.
2. Add missing backend and frontend tests listed in this document.
3. Simplify implementation only where tests now protect the behavior.
4. Fix frontend lint so the validation gate is reliable.
5. Re-run backend, frontend, and formatting verification commands.

Targeted cleanup and simplification areas:

- Delete obsolete `TransactionAccountCurrencyValidator` and stale currency-mismatch tests. Cross-currency account selection is now valid when required rates exist.
- Make `CurrencyExchangeRateSynchronizer` explicitly validate the full replacement set before deleting existing rows, so atomic replacement behavior is obvious.
- Simplify `CurrencyConversionService` by normalizing currency codes once and keeping direct, inverse, and cross-rate lookup helpers small.
- Extract split conversion/building from `ManageLedgerImpl.buildTransaction` into a focused private helper only if it improves readability without changing ownership or transaction boundaries.
- Clarify the frontend transaction total display policy for mixed-currency rows. Current source-currency-split fallback logic should be covered by tests or replaced with a simpler, explicit rule.
- Prefer narrow changes over broad formatting or unrelated refactoring.

## 12. Known Follow-Up Work

Before implementation is considered complete:

1. Remove obsolete `TransactionAccountCurrencyValidator` usage, class, and stale tests.
2. Add missing backend tests listed above.
3. Add missing cross-currency ledger tests listed above.
4. Add missing frontend tests listed above.
5. Improve implementation using the targeted cleanup areas above.
6. Fix frontend lint gate.
7. Re-run backend, frontend, and formatting verification commands.

## 13. Future Work

After this iteration:

1. Read base currency from `app_settings`.
2. Add an app-settings API and first-run configuration flow.
3. Convert dashboard and reporting totals into the configured base currency if desired.
4. Add manual rate refresh or synchronization status only if operationally necessary.
