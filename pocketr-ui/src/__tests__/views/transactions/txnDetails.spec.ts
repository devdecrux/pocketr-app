import { describe, expect, it } from 'vitest'
import { buildTxnDetails, txnCategoryNames } from '@/views/transactions/txnDetails'
import { buildSplit, buildTxn } from '@/__tests__/support/fixtures'

const lookups = {
  accountName: (id: string) => ({ daily: 'Daily account', living: 'Living expenses' })[id],
  categories: [{ id: 'c1', name: 'Groceries' }],
  minorUnit: () => 2,
}

describe('buildTxnDetails', () => {
  it('describes a simple transaction with its route and category only', () => {
    const details = buildTxnDetails(buildTxn(), lookups)

    expect(details.route).toEqual({ from: 'Daily account', to: 'Living expenses' })
    expect(details.categories).toEqual(['Groceries'])
    expect(details.splits).toEqual([])
  })

  it('lists split amounts for a mixed-currency transaction', () => {
    const details = buildTxnDetails(
      buildTxn({
        currency: 'USD',
        splits: [
          buildSplit({
            accountId: 'daily',
            side: 'CREDIT',
            accountCurrency: 'USD',
            amountMinor: 1000,
          }),
          buildSplit({ accountId: 'living', side: 'DEBIT', amountMinor: 920 }),
        ],
      }),
      lookups,
    )

    expect(details.splits.map((entry) => [entry.label, entry.amount])).toEqual([
      ['Daily account', '-$10.00'],
      ['Living expenses', '+€9.20'],
    ])
  })

  it('lists every split of a multi-split transaction and joins the destinations', () => {
    const details = buildTxnDetails(
      buildTxn({
        splits: [
          buildSplit({ accountId: 'daily', side: 'CREDIT', amountMinor: 300 }),
          buildSplit({ accountId: 'living', side: 'DEBIT', amountMinor: 100 }),
          buildSplit({
            accountId: 'x',
            accountName: 'Snapshot x',
            side: 'DEBIT',
            amountMinor: 200,
          }),
        ],
      }),
      lookups,
    )

    expect(details.splits).toHaveLength(3)
    expect(details.route.to).toBe('Living expenses, Snapshot x')
  })
})

describe('txnCategoryNames', () => {
  it('returns unique names from the store, then the split snapshot', () => {
    const names = txnCategoryNames(
      buildTxn({
        splits: [
          buildSplit({ accountId: 'a', side: 'DEBIT', categoryTagId: 'c1' }),
          buildSplit({ accountId: 'b', side: 'DEBIT', categoryTagId: 'c1' }),
          buildSplit({
            accountId: 'c',
            side: 'DEBIT',
            categoryTagId: 'c9',
            categoryTagName: 'Deleted',
          }),
          buildSplit({ accountId: 'd', side: 'DEBIT' }),
        ],
      }),
      [{ id: 'c1', name: 'Groceries' }],
    )

    expect(names).toEqual(['Groceries', 'Deleted'])
  })
})
