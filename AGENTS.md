# Repository Guidelines

## Project Structure & Module Organization
- `pocketr-api/`: Kotlin + Spring Boot backend.
  - Main code: `pocketr-api/src/main/kotlin/com/decrux/pocketr/api`
  - Tests: `pocketr-api/src/test/kotlin`
- `pocketr-ui/`: Vue 3 + TypeScript frontend.
  - App code: `pocketr-ui/src` (`api/`, `stores/`, `components/`, `utils/`)
  - Unit tests: `pocketr-ui/src/__tests__`
  - E2E tests: `pocketr-ui/e2e`
- Root infrastructure/docs:
  - `docker-compose.yaml` and `config/` (Traefik) are development-only for local app deployment and debugging.
  - `docs/`, `bruno/` (API collection), `storage/`.

## Build, Test, and Development Commands
- Start local infra (root): `docker compose up -d db traefik-reverse-proxy`
- Stop infra: `docker compose down`
- Backend dev server: `cd pocketr-api && ./gradlew bootRun`
- Backend checks:
  - `./gradlew build` (compile, test, and package backend)
  - `./gradlew test` (all tests)
  - `./gradlew lint` (ktlint check)
  - `./gradlew lintFix` (auto-format Kotlin)
- Frontend dev server: `cd pocketr-ui && npm run dev`
- Frontend checks:
  - `npm run type-check`
  - `npm run lint`
  - `npm run test:unit`
  - `npm run test:e2e`
  - `npm run build`

## Coding Style & Naming Conventions
- Kotlin: 4-space indentation, `PascalCase` classes, `camelCase` methods/properties.
- Prefer constructor injection and focused services with explicit validation/authorization boundaries.
- Make the smallest possible, reversible patches to existing functionality; avoid breaking changes.
- Do not mix unrelated changes in the same patch/commit.
- Preserving existing behavior is the priority, unless a change explicitly fixes a bug or security issue.
- Keep backend and frontend contracts synchronized; API/DTO/schema changes must update both sides together.
- Apply all rules in **Security & Configuration Tips** to every change; those requirements are high priority.
- Database schema changes are allowed; Flyway/Liquibase migrations are not required in this repo because the database can be rebuilt on next startup.
- Wildcard imports are not allowed under any circumstances.
- Prioritize reusable, composable components/services in both backend and frontend to reduce duplication and improve maintainability.
- Following software-architect best practices and appropriate design patterns is top priority.
- Adding new dependencies or introducing new tools/technologies requires explicit user approval first.
- Vue/TS:
  - Components: `PascalCase.vue` (example: `DateRangePicker.vue`)
  - Composables: `useX.ts` (example: `useSessionManager.ts`)
  - Tests: `*.spec.ts` / `*Test.kt`
- Run format/lint tools before opening a PR.

## Agent Startup, Context & Output
- Treat `AGENTS.md` and task-relevant files as the single source of truth; check them first before spending tokens on broad codebase exploration.
- At the start of every Codex/Claude run (including multi-agent runs), explicitly confirm that `AGENTS.md` and all task-relevant files are loaded in context.
- Use only the smallest relevant set of files in context to improve signal quality and reduce token usage.
- If required files are missing from context, load them before proposing or applying changes.
- Keep responses concise: avoid large raw outputs and provide a single summarized paragraph explaining what was changed, where, and why.

## Testing Guidelines
- Backend: JUnit 5 + Mockito (`spring-boot-starter-*-test`).
- Frontend: Vitest (unit) + Playwright (e2e).
- Add/adjust tests for changed behavior, especially service-layer validation and permission logic.
- Use targeted runs for speed, e.g. `./gradlew test --tests "com.decrux.pocketr.api.services.ledger.*"`.

## Commit & Pull Request Guidelines
- History favors concise messages; use clear, scoped subjects, preferably `type: summary` (example: `chore: split ledger validators`).
- Keep commits focused to one concern.
- PRs should include:
  - What changed and why
  - Validation steps/commands run
  - Screenshots for UI changes
  - Notes on API or config changes

## Security & Configuration Tips
- Copy `.env.example` to `.env` for local overrides.
- Never commit secrets.
- If DB credentials are customized in `.env`, keep backend runtime env vars aligned (`DB_USER`, `DB_PASSWORD`).
- Treat security-sensitive paths as high-risk: authentication/authorization, input validation, redirects, and infrastructure exposure.
- Data integrity, data security, and exposure prevention are top priority for new features and maintenance across frontend, backend, and infrastructure.
- Apply security best practices by default (e.g., SQL injection prevention, transport security/MITM risk reduction, secure auth/session handling, and least-privilege access).
