# Pocketr UI v1 Implementation Plan (Vue 3 + TypeScript) — Ledger + Household Overlay

This plan assumes an existing Vue 3 + TypeScript frontend (`pocketr-ui`) with established patterns. Agents must **follow the existing codebase
conventions** (folder structure, API client style, store pattern, UI components, routing guards, error handling, i18n if present, etc.).  
The goal is to consume the new backend features: **multi-currency ledger, categories, household overlay, sharing, and cross-user transfers**.

> **Principle:** UI provides a **mode switch** (Individual vs Household overlay).  
> Ledger remains generic; UI maps user-friendly forms (Expense/Income/Transfer) into **splits**.

---

## 0) Prerequisites / First Agent Task (MANDATORY)

### 0.1 Repo reconnaissance checklist (do this before coding)

1. Identify how `pocketr-ui` currently organizes:
    - API calls (Axios? Fetch? generated client?)
    - State management (Pinia? Vuex? composables?)
    - Routing (Vue Router structure, auth guards)
    - UI system (component library? Tailwind? custom)
    - Forms & validation (VeeValidate? Zod? Yup?)
    - Error/toast pattern, loading indicators
2. Find existing pages:
    - Dashboard
    - Accounts
    - Transactions
    - Profile settings
3. Identify existing domain models already implemented (likely different).  
   Decide whether to extend them or add new modules in parallel.
4. Users can participate in one household at a time. If a user is already part of a household then the user cannot join another one household or
   create it's own. The user beforehand needs to leave the household he/she participates.
5. For representing data where possible and makes sense use tanstack/vue-table. For example showing accounts, transactions and other type of lists.

### 0.2 Backend endpoints mapping

Confirm backend route names (they may differ). UI plan assumes endpoints from backend plan:

- `/currencies`
- `/accounts`
- `/categories`
- `/ledger/transactions`
- `/ledger/accounts/{id}/balance`
- `/households`, `/households/{id}`
- `/households/{id}/invite`, `/households/{id}/accept-invite`
- `/households/{id}/shares`

Create a single `api/` mapping file so UI never hardcodes URLs in components.

---

## 1) UI Architecture (Recommended Modules)

### 1.1 Core layers

- **API layer**: typed client functions per resource
- **Domain models**: TS types for Account, Transaction, Split, Household, etc.
- **Stores/composables**: session state + mode + fetched lists
- **Views/pages**: Dashboard, Accounts, Transactions, Profile, Household settings
- **Shared UI components**: selectors, tables, forms, modals

### 1.2 Overlay mode model (critical)

Create a single source of truth for “current view mode”:

```ts
type ViewMode =
    | { kind: 'INDIVIDUAL' }
    | { kind: 'HOUSEHOLD'; householdId: string };
```

This mode determines:

- Which accounts list to fetch
- Which transactions to fetch
- Which summaries to show on dashboard
- Whether cross-user transfer UI is enabled

Persist mode in:

- store state + localStorage (optional)
- and reset gracefully if user loses access to household

---

## 2) TypeScript Types (Add under existing domain folder)

> Align naming with existing code conventions (camelCase, PascalCase, etc.).

### 2.1 Currency

```ts
export interface Currency {
    code: string;        // 'EUR'
    minorUnit: number;   // 2
    name: string;        // 'Euro'
}
```

### 2.2 Account

```ts
export type AccountType = 'ASSET' | 'LIABILITY' | 'INCOME' | 'EXPENSE' | 'EQUITY';

export interface Account {
    id: string;
    ownerUserId: string;
    name: string;
    type: AccountType;
    currency: string; // currency code
    isArchived: boolean;
    createdAt: string;
}
```

### 2.3 Category tag

```ts
export interface CategoryTag {
    id: string;
    name: string;
}
```

### 2.4 Ledger transaction / splits

```ts
export type SplitSide = 'DEBIT' | 'CREDIT';

export interface LedgerSplit {
    id?: string;
    accountId: string;
    side: SplitSide;
    amountMinor: number;         // integer
    categoryTagId?: string | null;
    memo?: string | null;
}

export interface LedgerTxn {
    id: string;
    createdByUserId: string;
    householdId?: string | null;
    txnDate: string;             // YYYY-MM-DD
    description: string;
    currency: string;
    splits: LedgerSplit[];
    createdAt: string;
    updatedAt: string;
}
```

### 2.5 Household

```ts
export type HouseholdRole = 'OWNER' | 'ADMIN' | 'MEMBER';
export type MembershipStatus = 'INVITED' | 'ACTIVE';

export interface Household {
    id: string;
    name: string;
    createdBy: string;
    createdAt: string;
}

export interface HouseholdMember {
    householdId: string;
    userId: string;
    role: HouseholdRole;
    status: MembershipStatus;
    invitedBy?: string | null;
    invitedAt?: string | null;
    joinedAt?: string | null;
}

export interface HouseholdShare {
    householdId: string;
    accountId: string;
    sharedBy: string;
    sharedAt: string;
}
```

---

## 3) API Client Plan (Typed + Centralized)

### 3.1 Create resource clients

- `api/currencies.ts`
- `api/accounts.ts`
- `api/categories.ts`
- `api/ledger.ts`
- `api/households.ts`
- `api/shares.ts`

Ensure each function:

- returns typed data
- throws a typed error (or normalized error object)
- uses existing auth token mechanism

### 3.2 Example function signatures

```ts
export function listAccounts(params: { mode: 'INDIVIDUAL' | 'HOUSEHOLD'; householdId?: string }): Promise<Account[]>

export function createTxn(req: CreateTxnRequest): Promise<LedgerTxn>

export function listTxns(params: TxnQuery): Promise<LedgerTxn[]>

export function shareAccount(householdId: string, accountId: string): Promise<void>

export function inviteMember(householdId: string, payload: { emailOrUserId: string; role: 'ADMIN' | 'MEMBER' }): Promise<void>
```

> If backend uses userId invites later, UI can start with email input and show “pending invite” state.

---

## 4) State Management (Store/Composable Plan)

### 4.1 Session store (if exists)

Extend existing auth/session store with:

- `viewMode: ViewMode`
- `selectedHouseholdId` (derived)
- `households: Household[]` and `activeMemberships: HouseholdMember[]`

### 4.2 Data stores

Prefer one store per domain:

- `useCurrencyStore()`
- `useAccountStore()`
- `useCategoryStore()`
- `useLedgerStore()`
- `useHouseholdStore()`

Each store provides:

- `load...()` functions
- `isLoading`, `error`
- cached results per mode (optional)
- derived getters (e.g. accountsByType, sharedAccounts, etc.)

### 4.3 Cache policy (simple v1)

- On mode switch: refetch accounts + txns + dashboard summaries
- On create txn: optimistic add (optional) or refetch the page list

---

## 5) Routing & Navigation

### 5.1 Routes

Add routes if missing:

- `/household/:householdId/settings` → **HouseholdSettingsPage**
- Optionally `/household/select` → Household selection modal/page

### 5.2 Guards

- Require auth for all budgeting pages
- HouseholdSettingsPage guard:
    - user must be ACTIVE member
    - user must be role OWNER or ADMIN

### 5.3 Navigation UI additions

- Add a **Mode Switcher** (header/topbar):
    - Individual
    - Household: dropdown of accessible households (ACTIVE)
- Add menu entry for Household Settings visible only when:
    - mode = HOUSEHOLD
    - role = OWNER or ADMIN

---

## 6) Page-by-Page Implementation

## 6.1 Profile Settings Page (Create Household entry point)

### Requirements

- A section “Household budgeting”
- Button: “Create household”
- Input: household name
- On success:
    - Add household to store
    - Switch mode to HOUSEHOLD (new household)
    - Route user to Household Settings page
- In case a user is already part of another household he/she cannot create it's own household neither to take part in other households.

### UI details

- Disable create button during request
- Show inline validation:
    - name required, min length (e.g. 3)
- Show toast on success/failure (match existing pattern)

---

## 6.2 Household Settings Page (Owner and Admin(s) only)

### Sections (v1)

1) Household summary
    - household name
    - created date
2) Members list
    - show members + roles + statuses (INVITED/ACTIVE)
3) Invite member form
    - input: email (or userId if backend supports)
    - select role: ADMIN or MEMBER
    - submit invites (status=INVITED)
4) Shared accounts configuration
    - list of user’s own accounts (owned)
    - toggle share/unshare per account in this household (only the owner of the account can choose whether to share or not to the household. This
      needs to happen under Profile Settings)
    - show “shared by you” timestamp (optional)
5) (Optional) Household metadata actions
    - rename household (future)
    - delete household (future) — do not implement v1 unless backend supports

### Implementation rules

- Only owner or admin sees page (as per requirement).
- Invite uses `POST /households/{id}/invite`.
- Account shares use `POST/DELETE /households/{id}/shares`.

### UX for sharing

- Show your accounts grouped by type (ASSET/LIABILITY/…)
- Default view: show only ASSET accounts first (common)
- For each account:
    - toggle “Shared in household”
    - show currency and current balance (optional if balance endpoint exists)

---

## 6.3 Accounts Page (Individual vs Household mode)

### Requirements

- In Individual mode: show accounts owned by user.
- In Household mode:
    - show (a) your accounts and (b) accounts shared by other members, all visible in this household.
    - clearly label ownership (e.g. owner display name)
    - indicate if account is “shared” or not (for your own accounts)
- Account creation remains always “owned by me” (even in household mode).

### UI details

- Filters: type, currency, archived toggle
- Balance display:
    - if backend has balance endpoint: fetch balances in batch (or lazy-load per row)
    - else compute from transaction list (not recommended; too heavy)
- Account cards/table should show:
    - name, type, currency, balance
    - owner (in household mode)
    - actions: rename/archive (only if owned by me)

### Household share indicator

- For accounts owned by me:
    - show badge: “Shared” if shared in currently selected household

---

## 6.4 Transactions Page (Entry + Listing)

### Listing requirements

- Individual mode: list transactions affecting my accounts (or createdBy me; pick consistent policy)
- Household mode: list transactions with `householdId = selected` (strict recommended)
- Filters:
    - date range (month default)
    - account
    - category tag
    - text search in description
- Display:
    - description, date, currency
    - derived type (Expense/Income/Transfer) computed client-side (optional)
    - splits preview (at least 2 lines: accounts + amounts)
    - tags shown for expense splits

### Create transaction UX (mapped to splits)

Provide 3 tabs/forms:

1) **Expense**
2) **Income**
3) **Transfer**

All forms produce `CreateTransactionRequest` with explicit splits.

#### Expense form fields

- date
- currency (auto from “pay-from account”)
- pay-from account (ASSET or LIABILITY if you allow credit card spending later)
- expense account (EXPENSE)
- amount
- category tag (optional or required depending UX)
- description (default based on tag/payee)
- memo (optional)

Splits:

- Pay-from account: `CREDIT amount`
- Expense account: `DEBIT amount` + `categoryTagId`

#### Income form fields

- date
- currency (from deposit account)
- deposit-to account (ASSET)
- income account (INCOME)
- amount
- description

Splits:

- Deposit account: `DEBIT amount`
- Income account: `CREDIT amount`

#### Transfer form fields

- date
- currency (must match both accounts in v1)
- from account (ASSET)
- to account (ASSET)
- amount
- description

Splits:

- From: `CREDIT amount`
- To: `DEBIT amount`

#### Cross-user transfer (household mode)

Only enabled when:

- mode=HOUSEHOLD
- both accounts are visible shared accounts
- currency matches

Additionally:

- Show a warning/info: “Transfers between household accounts are allowed. You cannot post expenses to other users’ accounts.”

Backend will reject invalid attempts; UI should prevent them too.

### Validation rules (frontend)

- amount > 0
- required fields present
- currency consistent with account currency
- for household cross-user transfer:
    - both accounts shared
    - only ASSET accounts (v1 strict)

---

## 6.5 Dashboard Page (Mode-aware summaries)

### v1 dashboard widgets

- Account balances list (top N)
- Monthly spending by category (current month) — only if report endpoint exists
- Monthly spending by expense account “group” — only if report endpoint exists
- Recent transactions (last 10)

All widgets must react to mode:

- Individual mode uses owned accounts/txns
- Household mode uses selected household txns

### Data strategy

- Prefer calling dedicated report endpoints:
    - `/ledger/reports/monthly?mode=...&period=...`
- Avoid fetching full transaction list for charts.

If backend does not yet provide report endpoints, UI can ship with:

- “Recent transactions”
- “Account balances”
  …and add charts once reporting endpoints exist.

---

## 7) Shared UI Components (Reusable)

### 7.1 Mode Switcher

- Dropdown with:
    - Individual
    - Households list (ACTIVE)
- Persist selection
- On switch: refresh stores

### 7.2 AccountSelector

- props: mode, allowedTypes, currency (optional)
- shows accounts grouped by owner (household mode)
- emits selected accountId

### 7.3 CurrencyAmountInput

- takes currency code and minorUnit
- formats display (e.g. 45.00)
- outputs `amountMinor` integer
- avoids floating arithmetic by parsing string safely

### 7.4 CategoryTagSelector

- search + create inline (optional)
- lists user’s tags

### 7.5 TransactionSplitPreview

- shows splits, with signs and account names
- highlights splits tied to selected account (optional)

---

## 8) Formatting & Minor Units (Do Not Mess This Up)

### 8.1 Convert display ↔ minor units

Implement utility:

- `formatMinor(amountMinor, currencyCode, minorUnit)` -> string
- `parseToMinor(inputString, minorUnit)` -> integer

Rules:

- never store floats in store state for money; always `amountMinor`
- parse with decimal normalization:
    - allow comma or dot depending current locale (if app supports)
- clamp to integer

---

## 9) Error Handling & UX

- Standardize API error normalization:
    - show backend validation errors (e.g. “debits != credits”, “currency mismatch”, “account not shared”)
- For transaction creation:
    - show field-level errors if possible
    - show toast and keep form data on failure
- Loading:
    - skeletons for lists, spinners for buttons

---

## 10) Testing Plan (Frontend)

### 10.1 Unit tests (components/utils)

- parse/format minor unit conversions
- derived txn kind (expense/income/transfer)
- mode switch persistence

### 10.2 Integration/E2E tests (recommended)

Scenarios:

1. Create household from profile settings → redirected to household settings
2. Share an account → appears in household mode accounts list
3. Create household transfer between two shared asset accounts (cross-user simulated with fixtures)
4. Attempt invalid cross-user expense posting → UI prevents and/or backend error displayed
5. Individual vs household dashboard changes after switch

Use existing test setup (Cypress/Playwright/Vitest) if present.

---

## 11) Agent Work Split (Parallelizable)

### Agent UI-A — Repo Recon + Conventions Doc

- Document existing patterns:
    - API client style
    - store/composable conventions
    - routing conventions
    - UI component patterns
- Produce short “how to extend” notes for other agents.

### Agent UI-B — API Layer + Types

- Add TS types for ledger/household/currency
- Implement typed API clients and error normalization

### Agent UI-C — Mode Switcher + Stores

- Implement viewMode store + persistence
- Household list loading + active membership mapping
- Hook mode changes to reload data

### Agent UI-D — Profile Settings (Create Household)

- Create household form + service call
- On success: switch mode + route to household settings

### Agent UI-E — Household Settings (Owner-only)

- Members list + invite form + share toggles
- Owner-only route guard and UI nav visibility

### Agent UI-F — Transactions Page Enhancements

- Mode-aware listing
- Create transaction modal with 3 tabs mapping to splits
- Cross-user transfer enablement

### Agent UI-G — Accounts Page Enhancements

- Mode-aware accounts list with owner labels
- Share indicators
- Balance display integration

### Agent UI-H — Dashboard Mode-aware Widgets

- Implement minimal widgets now, charts later
- Integrate monthly report endpoints if available

---

## 12) Implementation Order (Step-by-step)

### Phase 1 — Discovery + API + Mode

1. Recon: capture existing patterns (Agent UI-A).
2. Add TS types + API clients (UI-B).
3. Implement viewMode store + mode switcher UI (UI-C).
4. Hook mode to accounts/transactions fetch (UI-C).

### Phase 2 — Household creation & settings

5. Profile settings: create household (UI-D).
6. Household settings page + owner-only guard + nav item (UI-E).
7. Share/unshare accounts UI (UI-E).

### Phase 3 — Core consumption pages

8. Accounts page: mode-aware list + labels (UI-G).
9. Transactions page: mode-aware list + create forms → splits (UI-F).

### Phase 4 — Dashboard

10. Dashboard: mode-aware summaries and recent txns (UI-H).
11. Add chart-ready report consumption when backend ready (UI-H).

### Phase 5 — QA

12. Unit tests for money utils and key components.
13. E2E flows for household creation, sharing, transfers.

---

## 13) “Don’t Break Existing UI” Guardrails

- Do not rewrite existing pages wholesale. Extend and refactor incrementally.
- Keep new UI additions behind feature flags if the repo uses them.
- Prefer adding new components under existing component directories.
- Keep the “ledger split model” internal: UI forms can still look simple.
- Avoid heavy client-side aggregation for charts; use report endpoints.

---

## Appendix A — CreateTransactionRequest UI payload shape (recap)

```ts
export interface CreateTxnRequest {
    mode: 'INDIVIDUAL' | 'HOUSEHOLD';
    householdId?: string | null;
    txnDate: string;        // YYYY-MM-DD
    currency: string;       // e.g. EUR
    description: string;
    splits: Array<{
        accountId: string;
        side: 'DEBIT' | 'CREDIT';
        amountMinor: number;
        categoryTagId?: string | null;
        memo?: string | null;
    }>;
}
```

---

## Appendix B — Client-side derived transaction type (optional)

Given loaded splits + account types:

- contains EXPENSE → Expense
- contains INCOME → Income
- else → Transfer/Balance

Use this only for UI labeling.
