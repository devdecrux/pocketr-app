# Pocketr App Claude Execution Playbook

Date: February 24, 2026  
Target: Claude Code + sub-agents  
Goal: Apply the review findings safely while preserving all existing functionality.

## 1. Mission and Non-Negotiables

Mission:
Refactor and harden the project with zero functional regressions across backend, frontend, and infrastructure.

Non-negotiables:

1. Existing behavior must keep working unless an item explicitly fixes a bug/security issue.
2. Every change must be covered by automated tests or a documented manual verification checklist.
3. No broad rewrites; use small, reversible, isolated patches.
4. Do not mix unrelated concerns in one task.
5. Preserve public API contracts unless versioning/migration is explicitly planned.

## 1A. Low-Token Operating Mode

Apply this mode for all Claude and sub-agent runs.

Token rules:

1. Do not paste full files unless explicitly requested.
2. Limit code excerpts to only changed hunks.
3. Keep progress updates to 5 lines max.
4. Reuse card IDs (`SEC-01`, `BUG-01`) instead of repeating full task text.
5. Final per-card report must use the compact schema in Section 7.

Context loading rules:

1. Start with `rg -n` and read only nearby lines.
2. Open broader file ranges only when blocked.
3. Never re-read files already summarized unless they changed.
4. Avoid reading generated outputs (`dist`, build artifacts) unless required.

Execution rules:

1. Run targeted tests for the card first.
2. Run full phase gate tests only at phase boundary.
3. Stop immediately on failure and fix before continuing.

## 2. Baseline Safety Gates (Run Before Any Refactor)

From repo root:

```bash
docker compose up -d db traefik-reverse-proxy
```

Backend baseline:

```bash
cd pocketr-api
./gradlew test
```

Frontend baseline:

```bash
cd pocketr-ui
npm run test:unit
npm run build
```

Record baseline status in a markdown log:

- Passed/failed commands
- Known flaky tests
- Existing warnings not introduced by this work

## 3. Agent Team Topology

Use one lead architect agent plus focused implementer agents.

Token budget rule:
Run at most 2 implementer sub-agents in parallel and 1 reviewer agent, then merge.

Lead Architect Agent responsibilities:

1. Enforce dependency order.
2. Prevent scope drift.
3. Validate that each task preserves behavior.
4. Approve merges only after gates pass.

Specialist implementer agents:

1. `security-backend` (report access policy)
2. `security-frontend` (redirect sanitization)
3. `infra-hardening` (Traefik + secrets + DB exposure)
4. `bugfix-ledger-ui` (household balance query propagation)
5. `bugfix-account-visibility` (household account listing consistency)
6. `refactor-backend-ledger` (god method decomposition)
7. `refactor-frontend-stores` (store coupling reduction)
8. `refactor-duplication` (ownership guards + transaction tab strategy)
9. `schema-policy` (alpha `ddl-auto` profile strategy + reset workflow)

## 4. Delivery Strategy

Implementation order is mandatory.

### Phase A: Critical Security and Data Exposure

Tasks:

1. `SEC-01` household report authorization guard.
2. `SEC-02` Traefik dashboard hardening + HTTPS entrypoint scaffolding.
3. `SEC-03` DB credential and exposure hardening.
4. `SEC-04` login redirect sanitization.

Why first:
These are high-risk and should be closed before architectural refactoring.

### Phase B: Functional Bug Corrections

Tasks:

1. `BUG-01` propagate `householdId` in account balance requests.
2. `BUG-02` fix household shared-account visibility contract.

Why second:
These are correctness issues with clear user impact and minimal architectural risk.

### Phase C: Architecture and Maintainability Refactors

Tasks:

1. `CQ-01` + `ARCH-01` split `ManageLedgerImpl.createTransaction`.
2. `ARCH-02` decouple store orchestration and reset flow.
3. `CQ-02` centralized ownership guard.
4. `CQ-03` strategy-based transaction tab submission.

Why third:
These are high-value but higher regression risk, so they should follow security/bug stabilization.

### Phase D: Infrastructure Maturity

Tasks:

1. `INFRA-01` enforce alpha schema policy for `ddl-auto` (no Flyway/Liquibase required).
2. `ARCH-03` container-first compose routing (replace host-based routing where feasible).
3. `INFRA-03` environment-specific compose separation and secret policy checks.

Why last:
These changes are larger rollout concerns and should follow app-level stabilization.

## 5. Atomic Task Cards (Claude-Ready)

Each card is designed for one implementer agent and one review cycle.

### Card SEC-01

Objective:
Block unauthorized household monthly report access.

Primary files:

1. `pocketr-api/src/main/kotlin/com/decrux/pocketr.api/services/reporting/GenerateReportImpl.kt`
2. `pocketr-api/src/test/kotlin/com/decrux/pocketr.api/services/reporting/ReportingTest.kt`

Implementation steps:

1. Inject `ManageHousehold` into report service.
2. Add active membership check in `HOUSEHOLD` mode before repo call.
3. Return forbidden response for non-members.
4. Add regression tests (member allowed, non-member denied).

Acceptance criteria:

1. Non-member request receives `403`.
2. Existing individual mode reports still pass.
3. Existing report tests remain green.

Verification commands:

```bash
cd pocketr-api
./gradlew test --tests '*ReportingTest*'
./gradlew test
```

### Card SEC-04

Objective:
Prevent open redirect on login.

Primary files:

1. `pocketr-ui/src/views/auth/LoginPage.vue`
2. `pocketr-ui/src/__tests__/App.spec.ts` (or new auth navigation test)

Implementation steps:

1. Create redirect sanitizer function for internal routes only.
2. Use sanitizer before `router.push`.
3. Default to `/dashboard` when invalid.
4. Add unit test for malicious redirect input.

Acceptance criteria:

1. External absolute URLs are rejected.
2. Valid internal route redirects still work.

Verification commands:

```bash
cd pocketr-ui
npm run test:unit
npm run build
```

### Card BUG-01

Objective:
Fix household balance loading by propagating household context.

Primary files:

1. `pocketr-ui/src/api/ledger.ts`
2. `pocketr-ui/src/views/DashboardPage.vue`
3. `pocketr-ui/src/views/AccountsPage.vue`

Implementation steps:

1. Add optional `householdId` parameter in `getAccountBalance`.
2. Include it in query params when present.
3. Pass `modeStore.householdId` from household-mode callers.
4. Add/adjust tests for household mode request behavior.

Acceptance criteria:

1. No 403 for valid shared-account balance fetches in household mode.
2. Individual mode behavior unchanged.

Verification commands:

```bash
cd pocketr-ui
npm run test:unit
npm run build
```

### Card BUG-02

Objective:
Make household shared accounts visible through a consistent contract.

Primary files:

1. `pocketr-api/src/main/kotlin/com/decrux/pocketr.api/controllers/AccountController.kt`
2. `pocketr-api/src/main/kotlin/com/decrux/pocketr.api/services/account/ManageAccount.kt`
3. `pocketr-api/src/main/kotlin/com/decrux/pocketr.api/services/account/ManageAccountImpl.kt`
4. `pocketr-ui/src/stores/account.ts`
5. Related tests in account/household/ledger packages

Implementation options:

1. Preferred: backend supports `mode` + `householdId` for account listing.
2. Fallback: frontend merges personal + household endpoints while preserving current contract.

Acceptance criteria:

1. Household mode selectors include shared accounts.
2. Individual mode list remains unchanged.
3. No duplicate accounts in merged views.

Verification commands:

```bash
cd pocketr-api
./gradlew test
cd ../pocketr-ui
npm run test:unit
npm run build
```

### Card CQ-01 + ARCH-01

Objective:
Refactor `ManageLedgerImpl.createTransaction` without behavior changes.

Primary files:

1. `pocketr-api/src/main/kotlin/com/decrux/pocketr.api/services/ledger/ManageLedgerImpl.kt`
2. New collaborators under `services/ledger/` (validator/policy/factory)
3. `pocketr-api/src/test/kotlin/com/decrux/pocketr.api/services/ledger/LedgerTransactionValidationTest.kt`
4. `pocketr-api/src/test/kotlin/com/decrux/pocketr.api/services/ledger/HouseholdTransactionVisibilityTest.kt`

Implementation steps:

1. Snapshot current behavior with tests.
2. Extract validators/policies one by one.
3. Keep method orchestration equivalent.
4. Move transport exception mapping gradually (do not break API responses).

Acceptance criteria:

1. All ledger tests pass.
2. No response contract regressions.
3. Method complexity is reduced and responsibilities are isolated.

Verification commands:

```bash
cd pocketr-api
./gradlew test --tests '*LedgerTransactionValidationTest*'
./gradlew test --tests '*HouseholdTransactionVisibilityTest*'
./gradlew test
```

### Card ARCH-02

Objective:
Reduce frontend store coupling and session-reset fragility.

Primary files:

1. `pocketr-ui/src/stores/auth.ts`
2. `pocketr-ui/src/stores/account.ts`
3. related stores/composables introducing central reset orchestration

Implementation steps:

1. Move reset orchestration to a central utility/plugin.
2. Pass explicit context to store `load` methods where needed.
3. Keep current user-facing behavior unchanged during session expiry.

Acceptance criteria:

1. Session expiry still logs out and routes correctly.
2. Stores reset correctly with no stale state.
3. Reduced direct store-to-store imports.

Verification commands:

```bash
cd pocketr-ui
npm run test:unit
npm run build
```

### Card INFRA-01

Objective:
Apply alpha-safe schema strategy without introducing migration tooling.

Primary files:

1. `pocketr-api/src/main/resources/application.yaml`
2. Optional profile config file(s) for alpha reset behavior

Implementation steps:

1. Keep Flyway/Liquibase out of scope.
2. Implement profile-based `ddl-auto` policy (`update` for iterative local use, `create-drop` or `create` for reset/test profiles).
3. Add or document one command path for fast DB reset when schema changes break compatibility.
4. Ensure tests and local startup still work with the chosen profile defaults.

Acceptance criteria:

1. Team can intentionally break schema in alpha without migration scripts.
2. A clean reset path exists and is documented.
3. App and tests still start reliably under default dev/test profiles.

Verification commands:

```bash
cd pocketr-api
./gradlew test
```

## 6. Global Regression Gates (Must Pass Per Phase)

After each phase:

```bash
cd pocketr-api
./gradlew test
cd ../pocketr-ui
npm run test:unit
npm run build
```

After Phase A and B additionally run manual smoke:

1. Login and logout flow.
2. Dashboard report loading in individual mode.
3. Dashboard report loading in household mode.
4. Create expense/income/transfer in both modes.
5. Account balances visible for owned and shared accounts.

## 7. Prompt Templates for Claude Main Agent

Use these exact prompts to maximize consistency.

### Main orchestrator prompt (compact)

```text
Act as lead architect for pocketr-app.
Execute one card at a time from docs/pocketr_app_claude_execution_playbook_2026-02-24.md in Low-Token Mode.
Constraints:
1) Preserve existing behavior.
2) Minimal, reversible diffs only.
3) Run card tests first; phase gates at phase end.
4) If tests fail, fix before next card.
Output exactly:
STATUS: <PASS|FAIL>
CARD: <ID>
FILES: <comma-separated paths>
TESTS: <cmd=result; cmd=result>
RISKS: <one short line>
NEXT: <next card or blocker>
Start with SEC-01.
```

### Sub-agent implementer prompt (compact)

```text
Implement CARD <ID> only in Low-Token Mode.
Rules:
1) No unrelated edits.
2) Keep external behavior unchanged except intended fix.
3) Add/update only necessary tests.
4) Keep patch small.
Return exactly:
STATUS: <PASS|FAIL>
FILES: <paths>
PATCH_SUMMARY: <3 bullets max>
TESTS: <cmd=result>
RISKS: <one line>
```

### Reviewer sub-agent prompt (compact)

```text
Review CARD <ID> patch only.
Focus:
1) regressions
2) missing tests
3) contract breaks
4) security/data risks
Return exactly:
STATUS: <APPROVE|BLOCK>
BLOCKERS:
- <file:line issue> (empty if none)
GAPS:
- <missing test or edge case>
```

## 8. Merge and Rollback Policy

Policy:

1. One card per commit.
2. No squash of unrelated cards.
3. If a phase gate fails, stop and fix before new card work.
4. Keep rollback simple: revert the single card commit.

## 9. Completion Criteria

The playbook is complete only when:

1. All selected cards are implemented.
2. All phase regression gates pass.
3. Manual smoke checklist passes.
4. Lead architect signs off that no functional regressions were introduced.
