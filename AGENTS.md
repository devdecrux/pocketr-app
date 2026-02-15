# AGENTS.md

This file is the working contract for AI/code agents in this repository.
It is written for parallel execution and future maintenance.

## 1) Mission

Build and maintain Pocketr as a double-entry budgeting app with:

- multi-currency ledger
- household overlay (sharing + controlled cross-user transfers)
- Vue SPA on top of Kotlin/Spring backend

Use these planning docs as target behavior:

- `docs/budgeting_app_implementation_plan.md`
- `docs/pocketr_ui_implementation_plan.md`

Current code is partially implemented and not fully aligned with those plans.
Do not assume contracts are already correct end-to-end.

## 2) Repo Layout

- `pocketr-api/`: Kotlin + Spring Boot backend (not a monorepo workspace package)
- `pocketr-ui/`: Vue 3 + TypeScript frontend (Vite + Pinia + shadcn-vue + tanstack/vue-table)
- `docker-compose.yaml`: PostgreSQL + Traefik local dependencies
- `config/traefik/`: reverse-proxy routing
- `.run/`: IntelliJ run configs for local orchestration

## 3) Local Runbook

Infrastructure:

```bash
docker compose up -d db traefik-reverse-proxy
```

Backend:

```bash
cd pocketr-api
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Frontend:

```bash
cd pocketr-ui
npm run dev
```

Access via Traefik:

- `http://localhost/frontend/` -> frontend
- `http://localhost/api/...` -> backend

## 4) Backend Ground Truth

Key facts:

- Base context path: `/api`
- API versioning: `/v1/...`
- Auth: stateful session + CSRF cookie/header (not JWT)
- Layering: controller -> service -> repository
- Money: integer `amountMinor`; split sides are `DEBIT`/`CREDIT`
- Balance rules:
    - debit-normal: `ASSET`, `EXPENSE`
    - credit-normal: `LIABILITY`, `INCOME`, `EQUITY`

Core service files:

- `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/ledger/ManageLedgerImpl.kt`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/household/ManageHouseholdImpl.kt`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/reporting/GenerateReportImpl.kt`

Security and auth:

- `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/config/security/SecurityConfig.kt`
- login endpoint: `/api/v1/user/login`
- logout endpoint: `/api/v1/user/logout`
- register endpoint: `/api/v1/user/register`

Testing:

- Backend tests are mostly unit-style Mockito tests.
- `ApiApplicationTests` is disabled.
- `./gradlew test` expects PostgreSQL test DB config.

## 5) Frontend Ground Truth

Key conventions:

- `<script setup lang="ts">` Composition API
- Pinia setup stores
- HTTP client is `ky` in `src/api/http.ts`
- CSRF bootstrap in `src/api/csrf.ts`
- Router guard hydrates auth on first navigation (`src/router/index.ts`)
- Mode switch via `src/stores/mode.ts` and `src/components/ModeSwitcher.vue`
- Domain stores under `src/stores/`
- API wrappers under `src/api/`
- UI primitives under `src/components/ui/` (treat as library-style generated code)

Important view files:

- `pocketr-ui/src/views/DashboardPage.vue`
- `pocketr-ui/src/views/AccountsPage.vue`
- `pocketr-ui/src/views/TransactionsPage.vue`
- `pocketr-ui/src/views/SettingsPage.vue`
- `pocketr-ui/src/views/HouseholdSettingsPage.vue`

Testing:

- unit: `npm run test:unit`
- type-check: `npm run type-check`
- lint: `npm run lint`
- e2e scaffolding exists; current sample test is template-level.

## 6) Known Contract Mismatches (Must Be Resolved Deliberately)

Before implementing new features, verify and reconcile these gaps:

1. Account listing mode:

    - Frontend calls `GET /api/v1/accounts?mode=...&householdId=...`
    - Backend currently ignores those params and returns owner accounts only.

2. Reporting DTO naming mismatch:

    - Frontend expects `categoryId`/`categoryName`.
    - Backend returns `categoryTagId`/`categoryTagName`.

3. Household account visibility:

    - Backend has `GET /api/v1/households/{id}/accounts`.
    - Frontend account loading currently does not use it.

4. Household category label duplication:

    - Categories are user-scoped (`owner_user_id`), not household-scoped.
    - Household reporting/dashboard groups by `categoryTagId` and displays `categoryTagName`.
    - If two members each have a category named `Groceries`, household dashboards can show duplicate `Groceries` rows (different category IDs).
    - This is expected in current design and should be addressed later with an explicit product/contract decision.

Any agent touching either side of these contracts must update both backend and frontend in the same change set or stage compatible migrations.

## 7) Parallel Agent Protocol

Use this when running multiple agents in parallel.

### 7.1 Work Partitioning

Assign non-overlapping ownership by default:

- Agent A: backend entities/dtos/repositories/services/controllers
- Agent B: frontend types/api clients/stores
- Agent C: frontend pages/components integration
- Agent D: reporting + tests + infra/docs updates

Do not have two agents edit the same file concurrently.

### 7.2 Branching and Scope

- Keep each agent scoped to one vertical slice.
- Prefer small, mergeable increments with explicit contract notes.
- If endpoint/DTO changes are introduced, include frontend consumer update in same branch.

### 7.3 Pre-merge Checks

Backend-touching changes:

- `cd pocketr-api && ./gradlew test` (or document why not run)

Frontend-touching changes:

- `cd pocketr-ui && npm run type-check`
- `cd pocketr-ui && npm run lint`
- run targeted unit/e2e tests when behavior changes

### 7.4 Handoff Checklist

Every agent handoff must include:

1. Files changed
2. API/DTO contract changes
3. Validation performed
4. Remaining risks / follow-ups

## 8) Coding Guardrails

- Do not use floating point for persisted money values.
- Keep split amounts positive and side-driven.
- Preserve backend path conventions under `/api/v1/...`.
- Preserve session + CSRF auth flow.
- Avoid broad rewrites; extend incrementally.
- Prefer updating existing stores/api layers over hardcoding requests in views.
- Keep generated UI primitives in `src/components/ui/` minimal-touch unless necessary.

## 9) Immediate Priorities For New Work

When starting new feature work, prioritize in this order:

1. Reconcile API contract mismatches in section 6.
2. Enforce household membership constraints from planning docs.
3. Complete mode-aware account/transaction/reporting behavior end-to-end.
4. Add/expand automated tests for corrected contracts and invariants.
