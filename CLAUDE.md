# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Pocketr is a personal budgeting application with double-entry accounting. It consists of two co-located but independently built projects (not a
workspace monorepo):

- **pocketr-api/** — Kotlin/Spring Boot 4 backend (Gradle, Java 25, PostgreSQL)
- **pocketr-ui/** — Vue 3/TypeScript frontend (Vite, Tailwind CSS 4, shadcn-vue)

Currently at skeleton/MVP stage: authentication and avatar upload work, but core budgeting features (accounts, transactions, categories) are not yet
implemented.

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
./gradlew build                # Build + run tests
./gradlew bootRun --args='--spring.profiles.active=dev'  # Run dev server
./gradlew test                 # Run tests (requires PostgreSQL running)
```

### Frontend (pocketr-ui/)

```bash
npm run build          # Type-check + Vite build (parallel)
npm run type-check     # vue-tsc --build
npm run lint           # oxlint then eslint (both with --fix)
npm run format         # Prettier
npm run test:unit      # Vitest (all unit tests)
npm run test:unit -- --run src/__tests__/App.spec.ts  # Single unit test
npm run test:e2e       # Playwright (chromium, firefox, webkit)
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
- `config/security/` — Spring Security configuration (7 files)

**Auth:** Session-based (not JWT). Custom handlers return HTTP status codes instead of redirects (SPA pattern). CSRF via `XSRF-TOKEN` cookie with a
custom `SpaCsrfTokenRequestHandler` that supports both XOR-encoded and plain tokens. Max 1 session per user, 5-minute timeout.

**Public endpoints:** `/v1/user/register`, `/v1/user/login`, `/v1/internal/csrf-token` (dev only). Everything else requires authentication.

**Spring profiles:** `dev` (local PostgreSQL, port 8081), `prod` (env vars: DB_URL, DB_USERNAME, DB_PASSWORD), `test` (create-drop DDL).

**Database:** PostgreSQL 18, Hibernate `ddl-auto: update` (no migration tooling yet). Entities: `User` (implements `UserDetails` directly),
`UserRole`.

### Frontend (Vue 3/TypeScript)

**Key conventions:**

- Composition API with `<script setup lang="ts">` exclusively
- Pinia stores use setup function style (`defineStore` with Composition API)
- `@` path alias resolves to `./src`
- Base path is `/frontend/` (for Traefik routing)

**Routing:** Vue Router with `requiresAuth` and `guestOnly` meta flags. Router guard lazily hydrates auth from session on first navigation.

**State management:** Single Pinia `auth` store. Theme via `@vueuse/core` `useColorMode()`.

**HTTP client:** `ky` instance in `src/api/http.ts` — auto-attaches CSRF token from cookie on every request, includes credentials.

**UI components:** shadcn-vue (new-york style) with reka-ui headless primitives in `src/components/ui/`. Use `cn()` from `src/lib/utils.ts` for class
merging.

**Dual layout system:** Routes with `meta: { layout: 'auth' }` render without sidebar; authenticated routes get the `SidebarProvider` wrapper.
Controlled in `App.vue`.

### Infrastructure

**Dockerfile:** Multi-stage — Node builds frontend → JDK embeds frontend in Spring static resources and builds fat JAR → JRE runtime image.

**Traefik:** Routes `/api/*` → backend:8081, `/frontend/*` → Vite:5173. Root `/` redirects to `/frontend/`.

## Conventions

- Backend endpoints are versioned under `/v1/` with context path `/api` (full path: `/api/v1/...`)
- Frontend calls API at `/api/v1/...` (proxied by Vite in dev, Traefik in local, embedded in prod)
- DTOs are Kotlin data classes; entities are JPA classes with `allOpen` plugin for Hibernate
- Auth handlers in `config/security/` follow the naming pattern `Custom*Handler`
- Frontend formatting: no semicolons, single quotes, 100 char print width (Prettier config)
