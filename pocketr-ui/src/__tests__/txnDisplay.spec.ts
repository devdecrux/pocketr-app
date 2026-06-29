import { describe, expect, it } from 'vitest'
import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import { formatSplitAmount, formatTxnDisplayAmount } from '@/utils/txnDisplay'

function split(override: Partial<LedgerSplit>): LedgerSplit {
  return {
    id: 'split-1',
    accountId: 'acc-1',
    accountCurrency: 'USD',
    side: 'DEBIT',
    amountMinor: 1000,
    exchangeRate: '1',
    ...override,
  }
}

function txn(override: Partial<LedgerTxn>): LedgerTxn {
  return {
    id: 'txn-1',
    txnDate: '2026-02-24',
    description: 'Groceries',
    currency: 'USD',
    txnKind: 'EXPENSE',
    splits: [],
    createdAt: '2026-02-24T00:00:00Z',
    updatedAt: '2026-02-24T00:00:00Z',
    ...override,
  }
}

function minorUnit(currency: string): number {
  return currency === 'JPY' ? 0 : 2
}

describe('transaction display formatting', () => {
  it('uses only persisted source-currency split amounts for the transaction row total', () => {
    const transaction = txn({
      currency: 'USD',
      splits: [
        split({ accountId: 'cash', accountCurrency: 'USD', side: 'CREDIT', amountMinor: 1000 }),
        split({
          accountId: 'groceries',
          accountCurrency: 'EUR',
          side: 'DEBIT',
          amountMinor: 920,
          exchangeRate: '0.92',
        }),
      ],
    })

    expect(formatTxnDisplayAmount(transaction, minorUnit)).toBe('$10.00')
  })

  it('does not fall back to summing converted account-currency splits as source currency', () => {
    const transaction = txn({
      currency: 'USD',
      splits: [
        split({
          accountId: 'rent',
          accountCurrency: 'EUR',
          side: 'DEBIT',
          amountMinor: 920,
          exchangeRate: '0.92',
        }),
        split({
          accountId: 'card',
          accountCurrency: 'BGN',
          side: 'CREDIT',
          amountMinor: 1800,
          exchangeRate: '1.8',
        }),
      ],
    })

    expect(formatTxnDisplayAmount(transaction, minorUnit)).toBe('Mixed currencies')
  })

  it('formats persisted split amounts with each split account currency', () => {
    expect(
      formatSplitAmount(
        split({
          accountCurrency: 'EUR',
          side: 'DEBIT',
          amountMinor: 920,
          exchangeRate: '0.92',
        }),
        minorUnit,
      ),
    ).toBe('+€9.20')
  })
})
