import { vi } from 'vitest'
import { HTTPError } from 'ky'
import { useAuthStore } from '@/stores/auth'
import type { AuthUser } from '@/types/auth'
import type { Household, HouseholdAccountShare, HouseholdSummary } from '@/types/household'
import type { Account, AccountBalance, CategoryTag, Currency, LedgerSplit, LedgerTxn, MonthlyReportEntry, PagedResponse } from '@/types/ledger'

/** A ky `HTTPError` whose body is the backend's `{ message }` payload (exposed as `error.data`, as ky does). */
export function httpError(message: string, status = 400): HTTPError {
  const request = new Request('http://localhost/api')
  const response = new Response(JSON.stringify({ message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  const error = new HTTPError(response, request, {} as never)
  error.data = { message }
  return error
}

export const CURRENCIES: Currency[] = [
  { code: 'EUR', minorUnit: 2, name: 'Euro' },
  { code: 'USD', minorUnit: 2, name: 'US Dollar' },
]

export function buildUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    id: 1,
    email: 'alex@example.com',
    firstName: 'Alex',
    lastName: 'Morgan',
    language: 'en',
    rolloverDay: 1,
    avatar: null,
    ...overrides,
  }
}

/** Signs `user` (default: Alex Morgan, id 1) in by setting the auth store's user. */
export function signIn(overrides: Partial<AuthUser> = {}): AuthUser {
  const user = buildUser(overrides)
  useAuthStore().user = user
  return user
}

/** An active EUR asset account owned by user 1; override what the test cares about. */
export function buildAccount(overrides: Partial<Account> = {}): Account {
  return {
    id: 'acc-1',
    ownerUserId: 1,
    name: 'Checking',
    type: 'ASSET',
    currency: 'EUR',
    createdAt: '2026-01-01T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
    ...overrides,
  }
}

/** Accounts shared by the page specs: user 1 owns all but `jamie`; `old` is archived. */
export const ACCOUNTS: Account[] = [
  buildAccount({ id: 'daily', name: 'Daily account' }),
  buildAccount({ id: 'savings', name: 'Savings' }),
  buildAccount({ id: 'card', name: 'Credit card', type: 'LIABILITY' }),
  buildAccount({ id: 'salary', name: 'Salary', type: 'INCOME' }),
  buildAccount({ id: 'living', name: 'Living expenses', type: 'EXPENSE' }),
  buildAccount({ id: 'old', name: 'Old checking', status: 'ARCHIVED' }),
  buildAccount({ id: 'usd', name: 'USD cash', currency: 'USD' }),
  buildAccount({ id: 'jamie', name: 'Jamie wallet', ownerUserId: 2 }),
]

/** A zero-amount expense report entry for the Groceries category; override what the test cares about. */
export function buildReportEntry(overrides: Partial<MonthlyReportEntry> = {}): MonthlyReportEntry {
  return {
    expenseAccountId: 'exp-1',
    expenseAccountName: 'Groceries',
    expenseAccountStatus: 'ACTIVE',
    categoryTagId: 'cat-1',
    categoryTagName: 'Groceries',
    categoryTagColor: '#62a864',
    currency: 'EUR',
    netMinor: 0,
    ...overrides,
  }
}

export function buildSplit(overrides: Partial<LedgerSplit> = {}): LedgerSplit {
  const accountId = overrides.accountId ?? 'daily'
  const side = overrides.side ?? 'CREDIT'
  return {
    id: `${accountId}-${side}`,
    accountId,
    accountName: ACCOUNTS.find((account) => account.id === accountId)?.name ?? accountId,
    accountCurrency: 'EUR',
    side,
    amountMinor: 4680,
    exchangeRate: '1',
    ...overrides,
  }
}

/** A EUR grocery expense paid from the daily account; override what the test cares about. */
export function buildTxn(overrides: Partial<LedgerTxn> = {}): LedgerTxn {
  return {
    id: 't1',
    txnDate: '2026-09-20',
    description: 'Grocery market',
    currency: 'EUR',
    txnKind: 'EXPENSE',
    createdAt: '2026-09-20T08:00:00Z',
    updatedAt: '2026-09-20T08:00:00Z',
    splits: [
      buildSplit({ accountId: 'daily', side: 'CREDIT' }),
      buildSplit({ accountId: 'living', side: 'DEBIT', categoryTagId: 'c1' }),
    ],
    ...overrides,
  }
}

export function txnPage(
  content: LedgerTxn[],
  overrides: Partial<PagedResponse<LedgerTxn>> = {},
): PagedResponse<LedgerTxn> {
  return {
    content,
    page: 0,
    size: 15,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    ...overrides,
  }
}

export function buildCategory(overrides: Partial<CategoryTag> = {}): CategoryTag {
  return {
    id: 'c1',
    name: 'Groceries',
    color: '#62a864',
    createdAt: '2026-09-12T09:00:00Z',
    ...overrides,
  }
}

export function buildHouseholdSummary(overrides: Partial<HouseholdSummary> = {}): HouseholdSummary {
  return {
    id: 'hh-1',
    name: 'Morgan family',
    role: 'OWNER',
    status: 'ACTIVE',
    rolloverDay: 1,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

export function buildHousehold(overrides: Partial<Household> = {}): Household {
  return {
    id: 'hh-1',
    name: 'Morgan family',
    rolloverDay: 1,
    createdAt: '2026-01-01T00:00:00Z',
    members: [],
    ...overrides,
  }
}

/** Freezes only `Date` at a fixed day (2026-09-20, 10:00 local) so "today" defaults are stable. */
export function freezeToday(iso = '2026-09-20T10:00:00'): void {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(iso))
}

/** Balance (minor units) of each account in `ACCOUNTS`. */
export const BALANCES: Record<string, number> = {
  daily: 184500,
  savings: 100000,
  card: 32000,
  salary: 350000,
  living: 107500,
  usd: 5000,
  jamie: 20000,
}

/** Balance rows for `ids`, in the currency of the matching `ACCOUNTS` entry. */
export function balanceRows(ids: string[]): AccountBalance[] {
  return ids.map((id) => {
    const account = ACCOUNTS.find((candidate) => candidate.id === id)
    return {
      accountId: id,
      accountName: account?.name ?? id,
      accountType: account?.type ?? 'ASSET',
      currency: account?.currency ?? 'EUR',
      balanceMinor: BALANCES[id] ?? 0,
      asOf: '2026-09-20',
    }
  })
}

/** An account of another member shared into a household. */
export function buildShare(
  accountId: string,
  overrides: Partial<HouseholdAccountShare> = {},
): HouseholdAccountShare {
  return {
    accountId,
    accountName: ACCOUNTS.find((account) => account.id === accountId)?.name ?? accountId,
    ownerEmail: 'alex@example.com',
    ownerFirstName: 'Alex',
    ownerLastName: 'Morgan',
    sharedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}
