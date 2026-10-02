import { describe, expect, it } from 'vitest'
import { CalendarDate } from '@internationalized/date'
import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import {
  calendarDateToIso,
  formatIsoDate,
  formatPickerDate,
  formatPickerRange,
  isoToCalendarDate,
  todayIso,
} from '@/utils/dates'
import { getTxnAppearance } from '@/utils/txnAppearance'
import { buildTxnDetails, txnCategoryNames } from '@/utils/txnDetails'
import { groupTransactionsByDay } from '@/utils/txnGroups'

function split(
  accountId: string,
  side: LedgerSplit['side'],
  amountMinor: number,
  extra: Partial<LedgerSplit> = {},
): LedgerSplit {
  return {
    id: `${accountId}-${side}`,
    accountId,
    accountName: `Snapshot ${accountId}`,
    accountCurrency: 'EUR',
    side,
    amountMinor,
    exchangeRate: '1',
    ...extra,
  }
}

function txn(splits: LedgerSplit[], extra: Partial<LedgerTxn> = {}): LedgerTxn {
  return {
    id: 't1',
    txnDate: '2026-09-20',
    description: 'Test',
    currency: 'EUR',
    txnKind: 'EXPENSE',
    createdAt: '',
    updatedAt: '',
    splits,
    ...extra,
  }
}

describe('dates', () => {
  it('uses the local calendar day, not UTC', () => {
    expect(todayIso(new Date(2026, 8, 5, 0, 30))).toBe('2026-09-05')
    expect(todayIso(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31')
  })

  it('round-trips ISO strings and ignores unparsable ones', () => {
    expect(calendarDateToIso(isoToCalendarDate('2026-09-20'))).toBe('2026-09-20')
    expect(isoToCalendarDate('nope')).toBeUndefined()
    expect(isoToCalendarDate('')).toBeUndefined()
    expect(calendarDateToIso(undefined)).toBeUndefined()
    expect(formatIsoDate('nope')).toBe('nope')
    expect(formatIsoDate('2026-09-20')).toMatch(/20.*2026/)
  })

  it('formats picker dates as DD/MM/YYYY and a reversed range in order', () => {
    expect(formatPickerDate(new CalendarDate(2026, 9, 3))).toBe('03/09/2026')
    expect(formatPickerRange(new CalendarDate(2026, 9, 30), new CalendarDate(2026, 9, 1))).toBe(
      '01/09/2026 – 30/09/2026',
    )
  })
})

describe('groupTransactionsByDay', () => {
  const today = new Date(2026, 8, 20, 10)

  it('groups consecutive transactions as today, yesterday, earlier this month and older months', () => {
    const groups = groupTransactionsByDay(
      [
        { txnDate: '2026-09-21' },
        { txnDate: '2026-09-20' },
        { txnDate: '2026-09-20' },
        { txnDate: '2026-09-19' },
        { txnDate: '2026-09-18' },
        { txnDate: '2026-09-01' },
        { txnDate: '2026-08-31' },
        { txnDate: '2025-12-24' },
      ],
      today,
    )

    expect(groups.map((group) => [group.id, group.items.length])).toEqual([
      ['upcoming', 1],
      ['today', 2],
      ['yesterday', 1],
      ['earlierThisMonth', 2],
      ['month:2026-08', 1],
      ['month:2025-12', 1],
    ])
    expect(groups[4]!.monthDate).toEqual(new Date(2026, 7, 1))
  })

  it('does not merge non-consecutive groups, so the server order is kept', () => {
    const groups = groupTransactionsByDay(
      [{ txnDate: '2026-09-20' }, { txnDate: '2026-09-19' }, { txnDate: '2026-09-20' }],
      today,
    )

    expect(groups.map((group) => group.id)).toEqual(['today', 'yesterday', 'today'])
  })
})

describe('getTxnAppearance', () => {
  it('colours badges and amounts per kind and treats unknown kinds like transfers', () => {
    expect(getTxnAppearance('EXPENSE')).toMatchObject({
      badgeColor: 'error',
    })
    expect(getTxnAppearance('EXPENSE').amountClass).toContain('text-error')
    expect(getTxnAppearance('DEBT_PAYMENT').amountClass).toContain('text-error')
    expect(getTxnAppearance('TRANSFER').amountClass).toBe('text-muted')
    expect(getTxnAppearance('INCOME')).toMatchObject({ badgeColor: 'success' })
    expect(getTxnAppearance('INCOME').amountClass).toContain('success')
    expect(getTxnAppearance('DEBT_PAYMENT').badgeClass).toContain('orange')
    expect(getTxnAppearance('OPENING_BALANCE').badgeColor).toBe('neutral')
    expect(getTxnAppearance('TRANSFER').badgeColor).toBe('neutral')
    expect(getTxnAppearance('OPENING_DEBT').amountClass).toContain('warning')
    expect(getTxnAppearance('SOMETHING_ELSE')).toEqual(getTxnAppearance('TRANSFER'))
    expect(getTxnAppearance('TRANSFER').icon).toBe('i-lucide-arrow-left-right')
  })
})

describe('buildTxnDetails', () => {
  const lookups = {
    accountName: (id: string) => ({ daily: 'Daily account', living: 'Living expenses' })[id],
    categories: [{ id: 'c1', name: 'Groceries' }],
    minorUnit: () => 2,
  }

  it('describes a simple transaction with its route and category only', () => {
    const details = buildTxnDetails(
      txn([
        split('daily', 'CREDIT', 4680),
        split('living', 'DEBIT', 4680, { categoryTagId: 'c1' }),
      ]),
      lookups,
    )

    expect(details.route).toEqual({ from: 'Daily account', to: 'Living expenses' })
    expect(details.categories).toEqual(['Groceries'])
    expect(details.splits).toEqual([])
  })

  it('prefers current account names and falls back to the ledger snapshot', () => {
    const details = buildTxnDetails(
      txn([split('daily', 'CREDIT', 100), split('gone', 'DEBIT', 100)]),
      lookups,
    )

    expect(details.route).toEqual({ from: 'Daily account', to: 'Snapshot gone' })
  })

  it('lists split amounts for mixed-currency and multi-split transactions', () => {
    const mixed = buildTxnDetails(
      txn(
        [split('daily', 'CREDIT', 1000, { accountCurrency: 'USD' }), split('living', 'DEBIT', 920)],
        { currency: 'USD' },
      ),
      lookups,
    )
    expect(mixed.splits.map((entry) => [entry.label, entry.amount])).toEqual([
      ['Daily account', '-$10.00'],
      ['Living expenses', '+€9.20'],
    ])

    const multi = buildTxnDetails(
      txn([split('daily', 'CREDIT', 300), split('living', 'DEBIT', 100), split('x', 'DEBIT', 200)]),
      lookups,
    )
    expect(multi.splits).toHaveLength(3)
    expect(multi.route.to).toBe('Living expenses, Snapshot x')
  })
})

describe('txnCategoryNames', () => {
  it('returns unique names from the store, then the split snapshot', () => {
    const names = txnCategoryNames(
      txn([
        split('a', 'DEBIT', 1, { categoryTagId: 'c1' }),
        split('b', 'DEBIT', 1, { categoryTagId: 'c1' }),
        split('c', 'DEBIT', 1, { categoryTagId: 'c9', categoryTagName: 'Deleted' }),
        split('d', 'DEBIT', 1),
      ]),
      [{ id: 'c1', name: 'Groceries' }],
    )

    expect(names).toEqual(['Groceries', 'Deleted'])
  })
})
