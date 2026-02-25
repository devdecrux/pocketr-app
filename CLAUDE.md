# CLAUDE.md

Purpose: give Claude Code a compact, high-signal map of this repo so changes are safe, fast, and non-regressive.

## 1. Non-Negotiables

1. Preserve existing behavior unless the task explicitly fixes a bug/security issue.
2. Make small, reversible patches. Do not mix unrelated changes.
3. Run targeted tests for touched areas first, then broader gates before finishing.
4. Keep backend/frontend contracts consistent (query params, DTO field names, auth/session behavior).
5. Treat security-sensitive code paths as high-risk: auth, household access checks, redirects, infra exposure.

## 2. Repo Map

- `pocketr-api`: Spring Boot 4 + Kotlin + JPA backend.
- `pocketr-ui`: Vue 3 + Vite + Pinia frontend.
- `config/traefik`: Traefik static/dynamic config.
- `docker-compose.yaml`: local infra (Postgres + Traefik).
- `Dockerfile`: multi-stage build (frontend built into backend static resources).
- `docs/pocketr_app_full_review_2026-02-24.md`: full technical review and findings.
- `docs/pocketr_app_claude_execution_playbook_2026-02-24.md`: phase/card workflow for implementation.

## 3. Quick Start / Core Commands

Infra dependencies:

```bash
docker compose up -d db traefik-reverse-proxy
docker compose down
```

Backend:

```bash
cd pocketr-api
./gradlew test
./gradlew bootRun
```

Frontend:

```bash
cd pocketr-ui
npm run test:unit
npm run build
npm run dev
```

Notes:
- Backend toolchain: Java 25 / Kotlin 2.3.x.
- Frontend engine: Node `^20.19.0 || >=22.12.0`.

## 4. Architecture Snapshot

Backend (`pocketr-api`):
- Controllers in `controllers/*Controller.kt`.
- Business logic in `services/**`.
- Persistence in `repositories/**`.
- Security config in `config/security/**` with session auth + CSRF cookie/header flow.

Frontend (`pocketr-ui`):
- Router: `src/router/index.ts` with auth guard + guest-only routes.
- Global session handling: `src/api/http.ts` (401 hook) + `src/stores/auth.ts`.
- View mode state (`INDIVIDUAL` vs `HOUSEHOLD`): `src/stores/mode.ts` (persisted in localStorage).
- Domain stores: account/category/currency/household/ledger.

Infra:
- Traefik routes `/api` -> backend `:8081`, `/frontend` -> frontend `:5173`.
- Current routing relies on `host.docker.internal` in dynamic config.

## 5. High-Risk Hotspots (Read Before Editing)

1. Reporting authorization:
   - `GenerateReportImpl` household monthly report path lacks explicit membership guard.
2. Login redirect handling:
   - `src/views/auth/LoginPage.vue` uses `route.query.redirect` directly.
3. Household account/balance flows:
   - Frontend sends mode/household context in some calls; backend support is inconsistent.
4. Ledger transaction service complexity:
   - `ManageLedgerImpl.createTransaction` is large and mixes validation/auth/persistence.
5. Store coupling:
   - `auth` store resets many stores directly; some stores pull other stores internally.
6. Infra exposure:
   - Traefik dashboard enabled on HTTP.
   - Postgres creds are hardcoded in compose and port `5432` is exposed.

If your task touches any hotspot, run stricter verification and avoid broad refactors in the same patch.

## 6. Testing Reality (Important)

Backend tests exist and are meaningful:
- `services/reporting/ReportingTest.kt`
- `services/ledger/LedgerTransactionValidationTest.kt`
- `services/ledger/HouseholdTransactionVisibilityTest.kt`
- account/category/household service tests

Frontend unit test coverage is minimal (`src/__tests__/App.spec.ts`).

Frontend E2E (`e2e/vue.spec.ts`) is template-level and not a reliable regression suite yet.

Practical rule:
1. Add/adjust tests when you change behavior.
2. Use targeted backend suites for focused fixes.
3. Always run `npm run test:unit && npm run build` after frontend changes.

## 7. Alpha Database Policy

Current state:
- `pocketr-api/src/main/resources/application.yaml` uses `spring.jpa.hibernate.ddl-auto: update`.

Policy for this alpha phase:
1. Breaking schema changes are allowed.
2. Flyway/Liquibase is not required yet.
3. If schema changes are introduced, document reset expectations in PR/task notes.
4. Keep dev/test startup reliable after schema changes (tests must still pass).

## 8. Change Workflow (Recommended)

1. Confirm scope and identify touched backend/frontend contracts.
2. Apply one logical fix/refactor at a time.
3. Run targeted tests.
4. Run broader project gates:
   - `cd pocketr-api && ./gradlew test`
   - `cd pocketr-ui && npm run test:unit && npm run build`
5. Summarize:
   - files changed
   - behavior changes
   - test results
   - residual risks

## 9. Token-Efficient Working Style

1. Prefer `rg -n` and narrow reads; avoid dumping whole files unless needed.
2. Reuse identifiers (`SEC-01`, `BUG-01`, etc.) from docs instead of repeating long context.
3. Keep progress updates short and structured.
4. For reviews, report blockers first with exact file/line references.

## 10. Source of Truth Docs

For full prioritized findings and implementation sequencing:
- `docs/pocketr_app_full_review_2026-02-24.md`
- `docs/pocketr_app_claude_execution_playbook_2026-02-24.md`

Use this `CLAUDE.md` as the fast-start layer; use the docs above for deep execution detail.

