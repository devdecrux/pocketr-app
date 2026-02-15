# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Pocketr is a budgeting application using a double-entry ledger and household overlay model.
It consists of two co-located but independently built projects (not a workspace monorepo):

- **pocketr-api/** — Kotlin/Spring Boot 4 backend (Gradle, Java 25, PostgreSQL)
- **pocketr-ui/** — Vue 3/TypeScript frontend (Vite, Tailwind CSS 4, shadcn-vue)

Core budgeting domains are now implemented in backend and partially integrated in frontend:

- currencies
- accounts
- categories
- ledger transactions/splits
- balances and reporting endpoints
- households (membership/invites/account sharing)
- mode switch UI (individual vs household)

Do not assume frontend/backend contracts are fully aligned yet. Verify endpoint/DTO expectations before coding.

## Development Setup

Start infrastructure first, then run backend and frontend:

```bash
# Start PostgreSQL + Traefik reverse proxy
docker compose up -d db traefik-reverse-proxy

# Backend (runs on :8081 with dev profile)
cd pocketr-api && ./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (runs on :5173)
cd pocketr-ui && npm run dev
```

Access via Traefik at `http://localhost` — routes `/api` to backend, `/frontend` to Vite dev server.

## Build & Test Commands

### Backend (pocketr-api/)

```bash
./gradlew build
./gradlew bootRun --args='--spring.profiles.active=dev'
./gradlew test
```

Notes:

- Tests are mostly unit-style service tests with Mockito.
- `ApiApplicationTests` is currently disabled.
- Tests still assume a reachable PostgreSQL config.

### Frontend (pocketr-ui/)

```bash
npm run dev
npm run build          # runs type-check + build-only
npm run type-check
npm run lint           # oxlint + eslint (with --fix)
npm run format
npm run test:unit
npm run test:e2e
```

## Architecture

### Backend (Kotlin/Spring Boot)

**Base package:** `com.decrux.pocketr_api`

**Layered architecture:** Controller → Service → Repository

- `controllers/` — REST controllers under `/v1/` (context path is `/api`)
- `services/` — Business logic, organized by domain (e.g., `user_registration/`, `user_avatar/`). Services use interface + impl pattern (e.g.,
  `RegisterUser` interface, `RegisterUserImpl`).
- `repositories/` — Spring Data JPA repositories
- `entities/db/` — JPA entities
- `entities/dtos/` — Request/response data classes
- `config/security/` — Spring Security + CSRF/session auth

**Auth:** Session-based (not JWT). Custom handlers return HTTP status codes instead of redirects (SPA pattern). CSRF via `XSRF-TOKEN` cookie with a
custom `SpaCsrfTokenRequestHandler` that supports both XOR-encoded and plain tokens. Max 1 session per user, 5-minute timeout.

**Public endpoints:** `/v1/user/register`, `/v1/user/login`, `/v1/internal/csrf-token` (dev only), frontend static files.

**Spring profiles:** `dev` (local PostgreSQL, port 8081), `prod` (env vars: DB_URL, DB_USERNAME, DB_PASSWORD), `test` (create-drop DDL).

**Database:** PostgreSQL 18, Hibernate `ddl-auto: update` (no migration tooling yet).

### Backend Domains (current)

- `controllers/AccountController.kt` — create/list/update account
- `controllers/CategoryController.kt` — category CRUD
- `controllers/CurrencyController.kt` — list currencies
- `controllers/LedgerController.kt` — create/list transactions, account balance
- `controllers/ReportingController.kt` — monthly expenses, account balances, timeseries
- `controllers/HouseholdController.kt` — create/list household, invite/accept, share/unshare, list household/shared accounts

Ledger invariants are enforced in service layer (`ManageLedgerImpl`):

- at least 2 splits
- positive split amounts
- valid split sides
- debits must equal credits
- transaction currency must match all split account currencies
- non-owned account posting requires household mode + shared-account checks
- cross-user posting is currently restricted to ASSET-only transfers

### Frontend (Vue 3/TypeScript)

**Key conventions:**

- Composition API with `<script setup lang="ts">` exclusively
- Pinia stores use setup function style (`defineStore` with Composition API)
- `@` path alias resolves to `./src`
- Base path is `/frontend/` (for Traefik routing)

**Routing:** Vue Router with `requiresAuth` and `guestOnly` meta flags. Router guard lazily hydrates auth from session on first navigation.

**State management:** Pinia setup stores by domain:

- `auth`
- `mode` (individual vs household)
- `account`
- `category`
- `currency`
- `ledger`
- `household`

**HTTP client:** `ky` instance in `src/api/http.ts` — auto-attaches CSRF token from cookie on every request, includes credentials.

**UI components:** shadcn-vue (new-york style) with reka-ui headless primitives in `src/components/ui/`. Use `cn()` from `src/lib/utils.ts` for class
merging.

**Dual layout system:** Routes with `meta: { layout: 'auth' }` render without sidebar; authenticated routes get the `SidebarProvider` wrapper.
Controlled in `App.vue`.

**Main feature views (implemented):**

- `DashboardPage.vue`
- `AccountsPage.vue` (table + create/rename)
- `TransactionsPage.vue` (table + expense/income/transfer creation)
- `SettingsPage.vue` (avatar + household create/accept/leave UI)
- `HouseholdSettingsPage.vue` (members/invite/share controls)

### Infrastructure

**Dockerfile:** Multi-stage — Node builds frontend → JDK embeds frontend in Spring static resources and builds fat JAR → JRE runtime image.

**Traefik:** Routes `/api/*` → backend:8081, `/frontend/*` → Vite:5173. Root `/` redirects to `/frontend/`.

## Conventions

- Backend endpoints are versioned under `/v1/` with context path `/api` (full path: `/api/v1/...`)
- Frontend calls API at `/api/v1/...` (proxied by Vite in dev, Traefik in local, embedded in prod)
- Money values are integer minor units (`amountMinor`), never floating point persisted values
- DTOs are Kotlin data classes; entities are JPA classes with `allOpen` plugin for Hibernate
- Auth handlers in `config/security/` follow the naming pattern `Custom*Handler`
- Frontend formatting: no semicolons, single quotes, 100 char print width (Prettier config)

## Known Frontend/Backend Mismatches (Important)

These exist in current code and should be reconciled intentionally:

1. `GET /api/v1/accounts` frontend sends `mode/householdId`, but backend currently ignores those params and returns owner accounts only.
2. Frontend monthly report expects `categoryId/categoryName`; backend uses `categoryTagId/categoryTagName`.
3. In household mode, category labels can appear duplicated in dashboard/reporting when different users have separate categories with the same name
   (e.g. both users have `Groceries`). Categories are user-scoped, but household reporting is grouped by category ID.

When touching one side of these contracts, update the other side in the same task or provide an explicit compatibility plan.
