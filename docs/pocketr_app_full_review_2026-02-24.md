# Pocketr App Full Review

Date: February 24, 2026  
Project: `pocketr-app`  
Scope: Backend (`Spring Boot`/`Kotlin`), Frontend (`Vue 3`), Infrastructure (`Docker`/`Traefik`/runtime config)

## Review Team

- Security Reviewer Agent
- Code Quality Reviewer Agent
- Bug Hunter Agent
- Architecture Reviewer Agent
- Infrastructure Reviewer Agent
- Software Architect Agent (final validation, de-duplication, prioritization, and pattern guidance)

## Executive Summary

Top priority issues are access control and deployment hardening:

1. Household report endpoint allows unauthorized data access.
2. Traefik dashboard and traffic are exposed without TLS/auth.
3. Database credentials are hardcoded and DB port is publicly mapped.

Main maintainability risks are concentrated in service/store coupling:

1. `ManageLedgerImpl.createTransaction` is a god method mixing business, auth, and transport concerns.
2. Account visibility behavior is inconsistent in household mode across backend and frontend.
3. Store dependencies are tightly coupled in `auth` and `account` flows.

---

## 1. Security Issues

### SEC-01: Household report authorization bypass

- Severity: High
- Evidence:
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/reporting/GenerateReportImpl.kt:42-46`
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/controllers/ReportingController.kt:24-31`
- Why this matters: Any authenticated user can request household reports for arbitrary `householdId` values.
- Pattern/Principle: Policy Object + Guard Clause (centralized authorization)
- Steps to address:
1. Inject `ManageHousehold` into `GenerateReportImpl`.
2. Before querying household report data, validate active membership (`isActiveMember`).
3. Return `403 FORBIDDEN` if the user is not an active member.
4. Add integration tests for allowed vs denied report access.
- Example:

```kotlin
// Before
MODE_HOUSEHOLD -> {
    val hId = householdId ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "householdId is required")
    ledgerSplitRepository.monthlyExpensesByHousehold(hId, monthStart, monthEnd, SplitSide.DEBIT, SplitSide.CREDIT)
}

// After
MODE_HOUSEHOLD -> {
    val hId = householdId ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "householdId is required")
    val userId = requireNotNull(user.userId)
    if (!manageHousehold.isActiveMember(hId, userId)) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not an active member of this household")
    }
    ledgerSplitRepository.monthlyExpensesByHousehold(hId, monthStart, monthEnd, SplitSide.DEBIT, SplitSide.CREDIT)
}
```

### SEC-02: Traefik dashboard and routes exposed on HTTP without authentication

- Severity: High
- Evidence:
  - `config/traefik/traefik.yml:1-7`
  - `config/traefik/dynamic.yml:1-53`
  - `docker-compose.yaml:22-24`
- Why this matters: Proxy dashboard visibility plus plaintext traffic increases reconnaissance and interception risk.
- Pattern/Principle: Defense in depth + secure defaults
- Steps to address:
1. Disable public dashboard exposure or protect it with auth middleware.
2. Add HTTPS (`websecure`) entrypoint and certificates.
3. Keep dashboard internal-only in non-dev environments.
4. Add environment-specific Traefik config overlays (`dev` vs `prod`).
- Example:

```yaml
# Before
entryPoints:
  web:
    address: ":80"
api:
  dashboard: true

# After
entryPoints:
  web:
    address: ":80"
  websecure:
    address: ":443"
    http:
      tls: {}
api:
  dashboard: true
  insecure: false
```

### SEC-03: Hardcoded Postgres credentials and exposed DB port

- Severity: High
- Evidence:
  - `docker-compose.yaml:7-13`
- Why this matters: Credentials are committed to source and the database is reachable from host network.
- Pattern/Principle: Least exposure + secret management
- Steps to address:
1. Remove `5432:5432` in non-local environments.
2. Replace hardcoded credentials with environment/secrets manager values.
3. Rotate database credentials after hardening.
4. Add `.env.example` without real secrets and git-ignore runtime secret files.
- Example:

```yaml
# Before
ports:
  - "5432:5432"
environment:
  POSTGRES_USER: pocketr_user
  POSTGRES_PASSWORD: pocketr_password

# After
environment:
  POSTGRES_USER: ${DB_USER}
  POSTGRES_PASSWORD: ${DB_PASSWORD}
# no host port mapping outside local dev
```

### SEC-04: Open redirect risk on login

- Severity: Medium
- Evidence:
  - `pocketr-ui/src/views/auth/LoginPage.vue:52-54`
- Why this matters: Crafted links can redirect authenticated users to attacker-controlled destinations.
- Pattern/Principle: Validated Redirect pattern
- Steps to address:
1. Validate redirect targets as internal routes only.
2. Default to `/dashboard` when validation fails.
3. Reuse a shared redirect sanitizer in all auth entry points.
- Example:

```ts
// Before
const requestedRedirect = route.query.redirect
const redirectTarget = typeof requestedRedirect === 'string' ? requestedRedirect : '/dashboard'
await router.push(redirectTarget)

// After
const redirectTarget = sanitizeInternalRedirect(route.query.redirect) ?? '/dashboard'
await router.push(redirectTarget)
```

---

## 2. Code Quality (Duplication, Readability, Maintainability, Clean Code)

### CQ-01: `createTransaction` has excessive responsibilities (god method)

- Severity: Medium
- Evidence:
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/ledger/ManageLedgerImpl.kt:31-177`
- Why this matters: Large sequential logic is hard to test, reason about, and safely change.
- Pattern/Principle: SRP + Application Service + Validator/Policy collaborators
- Steps to address:
1. Extract validation steps into dedicated validators (`SplitValidator`, `CurrencyValidator`).
2. Extract authorization checks into `TransactionPolicy`.
3. Keep orchestration in the service; move HTTP mapping to controller advice.
4. Add focused unit tests per validator/policy.
- Example:

```kotlin
// Before: single method does all validation + auth + persistence
override fun createTransaction(dto: CreateTransactionDto, creator: User): TransactionDto { ... }

// After: orchestrator delegates
override fun createTransaction(dto: CreateTransactionDto, creator: User): TransactionDto {
    splitValidator.validate(dto.splits)
    transactionPolicy.assertAllowed(dto, creator)
    val txn = transactionFactory.build(dto, creator)
    return ledgerTxnRepository.save(txn).toDto(userAvatarService)
}
```

### CQ-02: Repeated ownership checks across services

- Severity: Low
- Evidence:
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/account/ManageAccountImpl.kt:89-91`
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/category/ManageCategoryImpl.kt:54-56`
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/category/ManageCategoryImpl.kt:81-83`
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/household/ManageHouseholdImpl.kt:190-192`
- Why this matters: Duplicated authorization guard logic drifts over time and weakens consistency.
- Pattern/Principle: Policy Object / shared guard abstraction
- Steps to address:
1. Add `OwnershipGuard` utility/service with reusable guard methods.
2. Replace inline `if (owner mismatch)` checks with guard calls.
3. Centralize messages and exception mapping.
- Example:

```kotlin
class OwnershipGuard {
    fun requireOwner(resourceOwnerId: Long?, actorId: Long, message: String) {
        if (resourceOwnerId != actorId) throw ResponseStatusException(HttpStatus.FORBIDDEN, message)
    }
}
```

### CQ-03: Duplicated tab submit logic in `TransactionsPage.vue`

- Severity: Low
- Evidence:
  - `pocketr-ui/src/views/TransactionsPage.vue:374-463`
- Why this matters: The same validation and request construction is repeated three times.
- Pattern/Principle: Strategy + Builder
- Steps to address:
1. Create tab strategy objects (`expense`, `income`, `transfer`) containing specific split builders.
2. Share generic validation (`required fields`, `amount > 0`, `description`).
3. Build one submit path that uses selected strategy.
- Example:

```ts
const strategy = transactionStrategies[activeTab.value]
if (!strategy.validate(inputState)) {
  submitError.value = 'Please fill in all required fields and enter a positive amount.'
  return
}
const request = strategy.buildRequest(commonContext, inputState)
await createTxn(request)
```

---

## 3. Bugs

### BUG-01: Household balance requests omit `householdId`

- Severity: Medium
- Evidence:
  - `pocketr-ui/src/api/ledger.ts:28-31`
  - `pocketr-ui/src/views/DashboardPage.vue:81-86`
  - `pocketr-ui/src/views/AccountsPage.vue:173-178`
- Impact: Household balances can fail with `403` and appear blank/stale in UI.
- Root cause: The client helper does not include `householdId` query parameter.
- Steps to address:
1. Extend `getAccountBalance(accountId, asOf?, householdId?)`.
2. Pass `modeStore.householdId` from household-mode screens.
3. Add frontend tests for individual vs household mode request params.
- Example:

```ts
export function getAccountBalance(accountId: string, asOf?: string, householdId?: string) {
  const searchParams: Record<string, string> = {}
  if (asOf) searchParams.asOf = asOf
  if (householdId) searchParams.householdId = householdId
  return api.get(`${BASE}/accounts/${accountId}/balance`, { searchParams }).json<AccountBalance>()
}
```

### BUG-02: Shared household accounts not surfaced in account list flows

- Severity: Medium
- Evidence:
  - `pocketr-ui/src/stores/account.ts:40-43` (sends mode/householdId)
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/controllers/AccountController.kt:28-33` (ignores mode/householdId)
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/account/ManageAccountImpl.kt:78-81` (owner-only query)
- Impact: Household-shared accounts may not appear in selectors and account-driven flows.
- Root cause: Backend list endpoint always returns owner accounts only.
- Steps to address:
1. Extend backend list endpoint contract to include `mode` and optional `householdId`.
2. Add repository/service path for household-visible accounts.
3. Ensure membership validation before returning shared accounts.
4. Add regression tests for household mode account visibility.
- Example:

```kotlin
@GetMapping
fun listAccounts(
    @RequestParam(defaultValue = "INDIVIDUAL") mode: String,
    @RequestParam(required = false) householdId: UUID?,
    @AuthenticationPrincipal user: User,
): List<AccountDto> = manageAccount.listAccounts(user, mode, householdId)
```

### BUG-03: Reporting authorization bug also manifests as data-leak behavior

- Severity: High
- Evidence:
  - Same issue as `SEC-01` (`GenerateReportImpl.kt:42-46`)
- Impact: Functional behavior is incorrect (returns data for unauthorized household scope).
- Steps to address:
1. Implement `SEC-01` fix.
2. Add a negative integration test: non-member must receive `403`.
3. Add audit log entry on denied report access attempts.
- Example:

```kotlin
if (!manageHousehold.isActiveMember(hId, userId)) {
    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not an active member of this household")
}
```

---

## 4. Architecture

### ARCH-01: Service layer coupled to HTTP exceptions and domain orchestration

- Severity: Medium
- Evidence:
  - `pocketr-api/src/main/kotlin/com/decrux/pocketr_api/services/ledger/ManageLedgerImpl.kt:31-177`
- Why this matters: Domain logic is tightly coupled to transport concerns (`ResponseStatusException`), reducing reuse and testability.
- Pattern/Principle: Hexagonal architecture + domain exceptions + controller advice mapping
- Steps to address:
1. Replace transport exceptions inside domain/application services with domain-level exceptions/results.
2. Map domain exceptions to HTTP responses in a centralized `@ControllerAdvice`.
3. Isolate persistence adapters from domain logic.
- Example:

```kotlin
// Service throws domain exception
if (!isBalanced) throw DoubleEntryViolation(sumDebits, sumCredits)

// ControllerAdvice maps to HTTP 400
@ExceptionHandler(DoubleEntryViolation::class)
fun onDoubleEntryViolation(ex: DoubleEntryViolation) = ResponseEntity.badRequest().body(...)
```

### ARCH-02: Frontend store coupling and reset orchestration in `auth` store

- Severity: Medium
- Evidence:
  - `pocketr-ui/src/stores/account.ts:35-43`
  - `pocketr-ui/src/stores/auth.ts:49-65`
- Why this matters: Stores are not independently composable; auth logic imports and resets multiple domain stores directly.
- Pattern/Principle: Event-driven coordination + explicit dependency injection
- Steps to address:
1. Pass mode context as method arguments where needed (`load(modeCtx)`), not by importing other stores internally.
2. Move mass reset logic to a central session lifecycle module/plugin.
3. Have stores subscribe/react to session state changes.
- Example:

```ts
// Session lifecycle composable/plugin
sessionEvents.on('expired', () => resetAllStores(pinia))
```

### ARCH-03: Compose/Traefik architecture depends on host routing instead of containerized app services

- Severity: Medium
- Evidence:
  - `docker-compose.yaml:3-31` (only db + traefik services)
  - `config/traefik/dynamic.yml:48-53` (`host.docker.internal` routing)
- Why this matters: Local behavior depends on host process ports, reducing portability and scaling readiness.
- Pattern/Principle: Container-first composition + service discovery by network alias
- Steps to address:
1. Add backend and frontend services to compose.
2. Route Traefik to container service names (e.g., `http://backend:8081`) instead of host gateway.
3. Keep environment-specific overrides for local dev speed if needed.
- Example:

```yaml
services:
  backend:
    build: .
    networks: [pocketr-network]
  frontend:
    build: ./pocketr-ui
    networks: [pocketr-network]
# Traefik service URLs point to backend/frontend service names
```

---

## 5. Infrastructure

### INFRA-01: Alpha schema policy for `ddl-auto` (no migration tooling required)

- Severity: Medium
- Evidence:
  - `pocketr-api/src/main/resources/application.yaml:15-17`
- Why this matters: In alpha, breaking schema changes are acceptable, but `update` can still create inconsistent local states and hide model/schema mismatch issues.
- Pattern/Principle: Explicit alpha schema policy (profile-based behavior)
- Steps to address:
1. Keep Flyway/Liquibase out of scope for alpha.
2. Replace one-size-fits-all `ddl-auto` with profile-based strategy:
3. Use `update` only where preserving local seed data is needed.
4. Use `create-drop` (or `create`) in dedicated alpha reset/test profiles to force clean schema alignment.
5. Document quick DB reset workflow for developers and CI.
- Example:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
---
spring:
  config:
    activate:
      on-profile: alpha-reset
  jpa:
    hibernate:
      ddl-auto: create-drop
```

### INFRA-02: Lack of TLS termination in current Traefik setup

- Severity: High
- Evidence:
  - `config/traefik/traefik.yml:1-3`
  - `config/traefik/dynamic.yml:5-7`
- Why this matters: Requests over HTTP are vulnerable to interception/tampering.
- Pattern/Principle: Secure transport by default
- Steps to address:
1. Add TLS entrypoint and cert resolver.
2. Redirect HTTP to HTTPS.
3. Verify HSTS and secure cookie settings for production domains.
- Example:

```yaml
entryPoints:
  web:
    address: ":80"
  websecure:
    address: ":443"
http:
  routers:
    api-router:
      entryPoints: [websecure]
```

### INFRA-03: Secret handling and network exposure policy are environment-agnostic

- Severity: Medium
- Evidence:
  - `docker-compose.yaml:7-13`
  - `docker-compose.yaml:22-24`
- Why this matters: Same config pattern is reused without clear dev/prod separation, increasing accidental exposure risk.
- Pattern/Principle: Environment isolation + config as policy
- Steps to address:
1. Split compose files (`compose.dev.yml`, `compose.prod.yml`) with explicit overrides.
2. Keep sensitive values outside VCS and inject via environment/secrets provider.
3. Add CI checks to fail if placeholder secrets are committed.
- Example:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

## Prioritized Implementation Plan

### Wave 1 (Immediate: security and data protection)

1. Fix household report authorization (`SEC-01` / `BUG-03`).
2. Harden Traefik dashboard and enable TLS (`SEC-02`, `INFRA-02`).
3. Remove hardcoded DB credentials and restrict DB exposure (`SEC-03`).
4. Patch login redirect validation (`SEC-04`).

### Wave 2 (Functional correctness and architecture consistency)

1. Fix household balance query propagation (`BUG-01`).
2. Implement household-aware account listing contract (`BUG-02`).
3. Reduce service/store coupling (`CQ-01`, `ARCH-01`, `ARCH-02`).

### Wave 3 (Longer-term maintainability)

1. Remove duplication in ownership guards and transaction tab builders (`CQ-02`, `CQ-03`).
2. Enforce alpha DB schema policy (`INFRA-01`) with profile-based `ddl-auto` and reset workflow.
3. Containerize backend/frontend in compose and route by service name (`ARCH-03`).

## Definition of Done

1. Security tests prove unauthorized household report access returns `403`.
2. Frontend integration tests cover household balance and shared account visibility.
3. Infrastructure deploy path enforces TLS and no plaintext committed secrets.
4. Service-level unit tests exist for extracted validation/policy components.
5. Alpha schema policy is documented and implemented (`ddl-auto` profile strategy, no mandatory migration tooling yet).
