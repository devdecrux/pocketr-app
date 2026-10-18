# Repository Guidelines

## Project Structure & Module Organization
- `pocketr-api/`: Kotlin + Spring Boot backend.
  - backend code: `pocketr-api/src/main/kotlin/com/decrux/pocketr/api`
  - tests: `pocketr-api/src/test/kotlin/com/decrux/pocketr/api`
- `pocketr-ui/`: Vue 3 + TypeScript frontend.
  - App code: `pocketr-ui/src`
  - Unit tests: `pocketr-ui/src/__tests__`
  - E2E tests: `pocketr-ui/e2e`
- Root infrastructure/docs:
  - `docker-compose.dev.yaml` and `config/` (Traefik) are development-only for local app deployment and debugging.
  - `docker-compose.yaml` is the production-oriented Docker Compose file and currently provisions only PostgreSQL.
  - `docs/`, `bruno/` (API collection), `storage/`.

## Build, Test, and Development Commands
- As a final test step check with `./gradlew test` for broken tests
- `./gradlew ktlintFormat` (fix Kotlin formatting)

## Coding Style & Naming Conventions
- Kotlin: 4-space indentation, `PascalCase` classes, `camelCase` methods/properties.
- Prefer constructor injection and focused services with explicit validation/authorization boundaries.
- Make the smallest possible, reversible patches to existing functionality; avoid breaking changes.
- Do not mix unrelated changes in the same patch/commit.
- Preserving existing behavior is the priority, unless a change explicitly fixes a bug or security issue, or explicitly wanted.
- Keep backend and frontend contracts synchronized; API/DTO/schema changes must update both sides together.
- Apply all rules in **Security & Configuration Tips** to every change; those requirements are high priority.
- Database schema changes are allowed; Flyway/Liquibase migrations are not required in this repo because the database can be rebuilt on next startup.
- Wildcard imports are not allowed under any circumstances.
- Prioritize reusable, composable components/services in both backend and frontend to reduce duplication and improve maintainability.
- Following software-architect best practices and appropriate design patterns is top priority.
- Prefer Spring Data JPA derived query methods over explicit `@Query` declarations when possible.
- Adding new dependencies or introducing new tools/technologies requires explicit user approval first.
- Vue/TS:
  - Components: `PascalCase.vue` (example: `DateRangePicker.vue`)
  - Composables: `useX.ts` (example: `useSessionManager.ts`)
  - Tests: `*.spec.ts` / `*Test.kt`
- Run format/lint tools before opening a PR.

## Agent Startup, Context & Output
- `AGENTS.md` has higher priority than task-specific plans/docs/files. If there is any conflict, follow `AGENTS.md` or ask the user.
- Treat `AGENTS.md` and task-relevant files as the single source of truth; check them first before spending tokens on broad codebase exploration.
- At the start of every Codex/Claude run (including multi-agent runs), explicitly confirm that `AGENTS.md` and all task-relevant files are loaded in context. Show the confirmation only once at the start of the agent in the repository.
- Do not repeatedly reconfirm `AGENTS.md` usage during the same run; the initial confirmation is sufficient.
- Use only the smallest relevant set of files in context to improve signal quality and reduce token usage.
- If required files are missing from context, load them before proposing or applying changes.
- Keep responses concise: avoid large raw outputs and provide a single summarized paragraph explaining what was changed, where, and why. There is already a plugin called caveman being used. It takes precedence.

## Testing Guidelines
- Backend: JUnit 5 + Mockito (`spring-boot-starter-*-test`).
- Frontend: Vitest (unit) + Playwright (e2e).
- Add meaningful unit or integration tests in both backend and frontend. Creating a test for each small change is strongly unwanted. Tests bringing real value and code quality will be respected.
- Frontend tests exist to catch real bugs that can happen during development. Each test must check that our own business logic is correct, with finance logic as the top priority.
  - Before adding a test, ask: "Would this test fail because of a realistic bug in our code?" If not, do not write it.
  - Write tests for:
    - Financial logic: amounts in minor units, currency handling and totals, balances, transaction splits (debit/credit sides), rollover periods and date calculations.
    - Validation rules: what a form or strategy accepts or rejects before sending a request.
    - Requests: the payload and query params sent to the API.
    - Failure handling: the app state after a request fails (nothing lost, nothing wrongly removed, the error is kept).
    - Access rules: who can reach or change what, based on role, household membership and mode.
    - Configuration that is easy to break: routes, auth guards, i18n locale loading and message parity.
  - Do not write tests for:
    - Framework or library behaviour (Vue, Nuxt, Nuxt UI, Tailwind or any other dependency), such as modals or drawers opening and closing, tabs switching or keyboard shortcuts. Proving their product works is their job, not ours.
    - CSS, HTML or visual details: classes, colours/hex values, layout, responsive variants, markup structure. A test that checks whether a background is black or orange has no business value.
    - Static UI details: rendered text and labels, empty states, ARIA attributes, focus handling, or `disabled` attributes (unless the `disabled` state enforces a business rule).
    - Very small details, or logic already covered by another test.
  - How to write them:
    - Prefer testing extracted logic (utils, composables, strategies, stores) over mounting whole pages.
    - Assert on behaviour (requests sent, emitted values, computed results, navigation targets), not on markup or wording.
- Use targeted runs for speed, e.g. `./gradlew test --tests "com.decrux.pocketr.api.services.ledger.*"`.

## Commit & Pull Request Guidelines
- Use Conventional Commits for every commit: `<type>[optional scope]: <description>` (example: `chore: split ledger validators`).
- Release automation treats `feat` as a minor release, `BREAKING CHANGE:` footers or `!` markers as major releases, and other allowed Conventional Commit types as patch releases.
- The `master` branch is tagged automatically after merges; GitHub releases are built manually from a selected tag.
- Keep commits focused to one concern.
- `git commit *` or `git push *` commands needs to explicitly allowed by the user. You don't have permission to execute such commands.
- PRs should include:
  - What changed and why
  - Validation steps/commands run
  - Screenshots for UI changes
  - Notes on API or config changes

## Security & Configuration Tips
- Never commit secrets.
- Create a local `.env` manually only if you want to override the optional pgAdmin bootstrap defaults.
- Treat security-sensitive paths as high-risk: authentication/authorization, input validation, redirects, and infrastructure exposure.
- Data integrity, data security, and exposure prevention are top priority for new or existing features, maintenance across frontend, backend, and infrastructure.
- Apply security best practices by default (e.g., SQL injection prevention, transport security/MITM risk reduction, secure auth/session handling, and least-privilege access) and all related practices not mentioned here.
