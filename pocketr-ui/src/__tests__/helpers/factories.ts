import { HTTPError } from 'ky'
import type { Account, MonthlyReportEntry } from '@/types/ledger'

/** A ky `HTTPError` whose body is the backend's `{ message }` payload. */
export function httpError(message: string, status = 400): HTTPError {
  const request = new Request('http://localhost/api')
  const response = new Response(JSON.stringify({ message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  return new HTTPError(response, request, {} as never)
}

/** An active EUR asset account; override what the test cares about. */
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
