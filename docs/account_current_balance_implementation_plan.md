# Account Current Balance Read Model - Implementation Plan

This document describes how to add a scalable "current balance" fast path while keeping the ledger as the source of truth.

The feature is a CQRS-style read model:

- Write model: `ledger_txn` + `ledger_split` (authoritative)
- Read model: `account_current_balance` (derived, optimized for reads)

No API contract changes are required.

---

## 1) Goals

1. Reduce hot-path balance reads from aggregate scans to O(1) per account.
2. Preserve correctness and accounting invariants of double-entry posting.
3. Keep historical/as-of-date balances accurate via existing aggregate queries.
4. Keep rollout low-risk with reconciliation and fallback.

## 2) Non-goals

1. Replacing the ledger as source of truth.
2. Changing transaction posting semantics or DTO contract.
3. Solving all reporting scalability (this targets "current balance" first).

---

## 3) Design Pattern

Use a transactional projection (read model) updated in the same DB transaction as ledger posting.

Pattern summary:

1. Post transaction to ledger tables.
2. Compute per-account signed delta from splits.
3. Upsert/increment `account_current_balance`.
4. Commit once.

If posting fails, projection update also rolls back.

---

## 4) Data Model

Add one row per account in `account_current_balance`.

```sql
CREATE TABLE IF NOT EXISTS account_current_balance (
  account_id UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
  raw_balance_minor BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_current_balance_updated_at
  ON account_current_balance(updated_at);
```

Meaning of `raw_balance_minor`:

`SUM(DEBIT) - SUM(CREDIT)` for the account, across all posted splits.

Do not store sign-normalized balances in this table. Normalize in service layer based on account type:

- Debit-normal (`ASSET`, `EXPENSE`) => display `raw`
- Credit-normal (`LIABILITY`, `INCOME`, `EQUITY`) => display `-raw`

---

## 5) Write Path Integration

Integrate into `ManageLedgerImpl.createTransaction(...)` after building validated splits and before transaction commit.

### 5.1 Delta formula

For each split:

- `DEBIT`: `delta = +amountMinor`
- `CREDIT`: `delta = -amountMinor`

Aggregate by `accountId` first, then persist one increment per account.

### 5.2 Deadlock safety

Sort account IDs before issuing UPSERT statements so all concurrent transactions update rows in consistent order.

### 5.3 Repository method (example)

```kotlin
@Repository
interface AccountCurrentBalanceRepository : JpaRepository<AccountCurrentBalance, UUID> {

    @Modifying
    @Query(
        value = """
            INSERT INTO account_current_balance(account_id, raw_balance_minor, updated_at)
            VALUES (:accountId, :delta, now())
            ON CONFLICT (account_id) DO UPDATE
            SET raw_balance_minor = account_current_balance.raw_balance_minor + EXCLUDED.raw_balance_minor,
                updated_at = now()
        """,
        nativeQuery = true,
    )
    fun addDelta(accountId: UUID, delta: Long)
}
```

### 5.4 Service snippet (example)

```kotlin
private fun applyCurrentBalanceProjection(splits: List<LedgerSplit>) {
    val deltas = mutableMapOf<UUID, Long>()

    for (split in splits) {
        val accountId = requireNotNull(split.account?.id)
        val signed = if (split.side == SplitSide.DEBIT) split.amountMinor else -split.amountMinor
        deltas[accountId] = (deltas[accountId] ?: 0L) + signed
    }

    deltas.entries
        .sortedBy { it.key.toString() }
        .forEach { (accountId, delta) ->
            if (delta != 0L) accountCurrentBalanceRepository.addDelta(accountId, delta)
        }
}
```

Call `applyCurrentBalanceProjection(...)` inside the same `@Transactional` method as ledger save.

---

## 6) Read Path Strategy

Use read model for current-date requests only. Keep existing aggregate query for historical `asOf`.

Decision:

1. If `asOf == LocalDate.now()` => read from `account_current_balance`.
2. Else => existing `ledger_split` aggregate query path.

This gives immediate performance gain for dashboards without changing historical correctness.

### 6.1 Single balance flow

1. Keep existing access/permission checks.
2. Fetch account metadata (`type`, `currency`, etc.).
3. Get `raw_balance_minor` from read model or fallback aggregate.
4. Normalize by account type before returning DTO.

### 6.2 Multi-account balance flow

Add a batch read model query:

```sql
SELECT account_id, raw_balance_minor
FROM account_current_balance
WHERE account_id IN (:accountIds);
```

Missing rows should be treated as `0`.

---

## 7) Backfill and Rollout Plan

### Phase A - Add schema and code behind feature flag

1. Create table `account_current_balance`.
2. Add repository/entity/service projection code.
3. Add config flag:
   - `ledger.current-balance.fast-path-enabled=false`

### Phase B - Backfill existing data

```sql
INSERT INTO account_current_balance(account_id, raw_balance_minor, updated_at)
SELECT
  ls.account_id,
  COALESCE(SUM(CASE WHEN ls.side = 'DEBIT' THEN ls.amount_minor ELSE -ls.amount_minor END), 0),
  now()
FROM ledger_split ls
GROUP BY ls.account_id
ON CONFLICT (account_id) DO UPDATE
SET raw_balance_minor = EXCLUDED.raw_balance_minor,
    updated_at = now();
```

### Phase C - Verification

Compare projection and recomputed ledger totals:

```sql
WITH ledger_calc AS (
  SELECT
    ls.account_id,
    COALESCE(SUM(CASE WHEN ls.side = 'DEBIT' THEN ls.amount_minor ELSE -ls.amount_minor END), 0) AS raw_balance_minor
  FROM ledger_split ls
  GROUP BY ls.account_id
)
SELECT
  COALESCE(l.account_id, c.account_id) AS account_id,
  l.raw_balance_minor AS ledger_raw,
  c.raw_balance_minor AS current_raw
FROM ledger_calc l
FULL OUTER JOIN account_current_balance c ON c.account_id = l.account_id
WHERE COALESCE(l.raw_balance_minor, 0) <> COALESCE(c.raw_balance_minor, 0);
```

Expected result: zero rows.

### Phase D - Enable fast path

1. Turn on `ledger.current-balance.fast-path-enabled=true`.
2. Monitor p95 latency and DB CPU.
3. Keep fallback path available.

---

## 8) Reconciliation and Repair

Add periodic reconciliation (cron/job/ops script), for example daily.

If mismatch is found:

1. Log account IDs and diff.
2. Repair by recomputing only affected accounts:

```sql
WITH target AS (
  SELECT
    ls.account_id,
    COALESCE(SUM(CASE WHEN ls.side = 'DEBIT' THEN ls.amount_minor ELSE -ls.amount_minor END), 0) AS raw_balance_minor
  FROM ledger_split ls
  WHERE ls.account_id = :accountId
  GROUP BY ls.account_id
)
INSERT INTO account_current_balance(account_id, raw_balance_minor, updated_at)
SELECT account_id, raw_balance_minor, now() FROM target
ON CONFLICT (account_id) DO UPDATE
SET raw_balance_minor = EXCLUDED.raw_balance_minor,
    updated_at = now();
```

---

## 9) Edge Cases and Future-proofing

1. Transaction updates/deletes:
   - Current system mainly posts immutable entries.
   - If edit/delete is introduced later, projection must apply reverse delta or full recompute for affected accounts.
2. Backdated transactions:
   - Safe for current balance projection because current balance is cumulative.
   - Historical as-of values still rely on ledger aggregates.
3. Overflow:
   - Keep `BIGINT`; add guardrails if unusually high volumes are expected.

---

## 10) Test Plan

### 10.1 Unit tests

1. Delta mapping:
   - Debit split adds positive delta.
   - Credit split adds negative delta.
2. Multi-split same account aggregation.
3. Account-type sign normalization in response DTO.

### 10.2 Integration tests

1. Post transaction updates both ledger and projection in one transaction.
2. Simulated failure after ledger save rolls back projection update.
3. Fast path returns same value as aggregate path for `asOf=today`.
4. Historical `asOf` still uses aggregate path and remains unchanged.

### 10.3 Concurrency tests

1. Parallel posting to same account; final projection equals ledger recompute.
2. Parallel posting to overlapping account sets; no deadlocks after sorted write order.

---

## 11) Observability

Track and alert on:

1. API p95:
   - `/v1/ledger/accounts/{id}/balance`
   - `/v1/ledger/accounts/balances`
2. Projection write failures.
3. Reconciliation mismatches count.
4. Read-path split:
   - fast-path hit rate
   - aggregate fallback rate

Suggested log tags:

- `balance_path=fast|aggregate`
- `projection_delta_accounts=<count>`
- `reconciliation_mismatch=<true|false>`

---

## 12) Agent Work Split (parallelizable)

### Agent 1 - DB + repository

Files:

- new migration SQL (or SQL script)
- new `AccountCurrentBalance` entity/repository

Deliverables:

1. DDL + backfill SQL.
2. Upsert increment method.
3. Batch fetch method.

### Agent 2 - Ledger write path

Files:

- `ManageLedgerImpl`

Deliverables:

1. Delta aggregation function.
2. Transactional projection update call.
3. Deadlock-safe ordering.

### Agent 3 - Read path + feature flag

Files:

- `ManageLedgerImpl`
- config files

Deliverables:

1. Fast path for `asOf == today`.
2. Fallback aggregate path.
3. Feature flag wiring.

### Agent 4 - Tests

Files:

- ledger service tests
- repository integration tests

Deliverables:

1. Unit + integration + concurrency test coverage.
2. Regression checks for historical balances.

---

## 13) Acceptance Criteria

1. Current-date balance endpoints use read model when enabled.
2. Returned balances match aggregate baseline for identical requests.
3. Ledger posting and projection updates are atomic.
4. Reconciliation query shows no mismatch after backfill and normal posting.
5. Historical balance behavior is unchanged.
