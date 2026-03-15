# Liability Functionality - Implementation Brief for Agents

This document is the implementation brief for Claude Code / Codex agents.

The feature must be implemented with the least possible changes.

The highest priority is:

1. do not break existing functionality
2. do not rewrite existing accounting flows
3. add liability support as a small extension on top of the current system

## 1) What Must Be Implemented

Implement only these three things:

1. allow opening debt for `LIABILITY` account creation
2. add one new `Debt Payment` transaction flow
3. show debt repayments as spending in budgeting-oriented UI

Do not expand scope beyond that unless strictly necessary.

## 2) What Must Not Be Broken

These existing behaviors must keep working exactly as they do now:

- asset opening balance creation
- expense flow
- income flow
- transfer flow
- current balance logic
- liability balance calculation already present in the ledger
- existing reporting that depends on `EXPENSE` accounts

If a change is not directly required for liability support, do not make it.

## 3) Core Product Rule

Pocketr should treat a liability account as:

- the amount still owed

So:

- opening debt increases the liability balance
- repayment decreases the liability balance

In the budgeting experience:

- repayment should be shown to the user as spending

Important:

- this does **not** mean the ledger posting should become a normal `EXPENSE` posting
- the "spending" behavior should be implemented in classification / presentation

## 4) Ledger Examples

Use these examples as the source of truth for implementation.

### 4.1 Opening debt

Example:

- create liability account `Car Lease`
- opening debt: `50,000 EUR`

Required ledger effect:

- debit `Opening Equity` `50,000`
- credit `Car Lease` liability `50,000`

Result:

- liability balance becomes `50,000 EUR`

### 4.2 Repayment

Example:

- repay `1,000 EUR` from `Bank`

Required ledger effect:

- debit `Car Lease` liability `1,000`
- credit `Bank` asset `1,000`

Result:

- liability balance decreases by `1,000 EUR`
- bank balance decreases by `1,000 EUR`
- user sees this as spending in the budgeting UI

## 5) Minimal Technical Plan

### 5.1 Backend account creation

Current state:

- opening balance is allowed only for `ASSET`

Required change:

- allow opening amount for `LIABILITY` too

Keep the existing DTO if possible:

- `CreateAccountDto`

Preferred implementation approach:

- reuse the current create-account flow
- extend opening position logic instead of creating a completely separate liability system

Expected touched backend files:

- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/services/account/ManageAccountImpl.kt`
- `pocketr-api/src/main/kotlin/com/decrux/pocketr/api/services/account/OpeningBalanceServiceImpl.kt`
  or a small replacement/generalization of it

### 5.2 Account creation UI

Current state:

- opening balance fields are shown only for `ASSET`

Required change:

- show opening amount field for `LIABILITY`

Use simple wording:

- `ASSET`: `Initial balance`
- `LIABILITY`: `Opening debt`

For the normal UI:

- liability opening value should be positive-only

Expected touched frontend file:

- `pocketr-ui/src/views/AccountsPage.vue`

### 5.3 Transaction UI

Current state:

- UI has `Expense`, `Income`, `Transfer`
- none is a clean liability repayment flow

Required change:

- add exactly one new flow: `Debt Payment`

Recommended inputs:

- pay-from asset account
- liability account
- amount
- date
- description

Generated posting:

- debit liability
- credit asset

Expected touched frontend files:

- `pocketr-ui/src/views/TransactionsPage.vue`
- `pocketr-ui/src/utils/txnStrategies.ts`

Important:

- do not rewrite the existing `Expense`, `Income`, and `Transfer` flows
- add the new flow as a small, isolated extension

### 5.4 Budgeting presentation

Current state:

- accounting expense reporting is based on `EXPENSE` accounts

Required change:

- liability repayments should appear as spending in budgeting-oriented views

Important:

- do not change the underlying repayment posting
- do not force repayment into an `EXPENSE` account just to make it appear in spending

Keep this separation:

- ledger/accounting meaning: liability repayment
- budgeting/UI meaning: spending

## 6) Out of Scope

Do not implement any of these in this task:

- principal vs interest split
- fees and penalties
- amortization schedule
- lender metadata
- payoff projections
- general journal entry UI
- broad reporting rewrite
- changes to current balance architecture
- any refactor not required for liability support

## 7) Future-Proofing Rule

Keep the implementation compatible with future realistic accounting.

That means:

- keep the ledger posting technically correct
- keep "spending" as a UI/reporting classification concern
- do not use fake `EXPENSE` postings for debt repayment

If this rule is followed, future versions can later add:

- principal vs interest split
- fees
- richer debt reporting
- more advanced journal workflows

without replacing the core implementation.

## 8) Acceptance Criteria

The feature is complete when all of the following are true:

1. user can create a `LIABILITY` account with an opening debt amount
2. opening debt sets the liability balance correctly
3. user can record a `Debt Payment` from an asset account
4. repayment decreases the liability balance
5. repayment decreases the asset balance
6. repayment is visible to the user as spending in the budgeting experience
7. existing expense, income, transfer, and balance behavior still work

## 9) Agent Instructions

When implementing this feature:

1. prefer the smallest safe patch set
2. preserve existing behavior unless the change is required for liability support
3. avoid opportunistic refactors
4. add focused tests only for the new liability behavior
5. if a design choice would require rewriting existing flows, do not take that path

Additional guidance:

- if a slightly larger change is genuinely needed and produces better code readability, optimization, performance, future-proofing, or extensibility, it is acceptable
- this does not permit broad unrelated cleanup
- any larger change must still protect existing functionality and keep regression risk low

If there are two valid solutions, choose the one with:

- fewer touched files
- lower regression risk
- better reuse of the current code

If a broader solution is clearly better, choose it only when it also improves:

- readability and maintainability
- performance or efficiency
- future extensibility
- architectural clarity
