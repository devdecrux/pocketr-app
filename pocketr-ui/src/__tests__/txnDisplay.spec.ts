import { describe, expect, it } from 'vitest'
import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import {
  formatSplitAmount,
  formatTxnDisplayAmount,
  resolveSpendingAccountName,
} from '@/utils/txnDisplay'

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

describe('resolveSpendingAccountName', () => {
  const accounts = new Map([
    ['exp-groceries', { name: 'Groceries (store)', type: 'EXPENSE' as const }],
    ['liab-card', { name: 'Credit card', type: 'LIABILITY' as const }],
    ['asset-checking', { name: 'Checking', type: 'ASSET' as const }],
  ])
  const lookup = (id: string) => accounts.get(id)

  it('prefers the account store name (renames) for the EXPENSE debit split', () => {
    const expense = txn({
      txnKind: 'EXPENSE',
      splits: [
        split({ accountId: 'asset-checking', accountType: 'ASSET', side: 'CREDIT' }),
        split({
          accountId: 'exp-groceries',
          accountType: 'EXPENSE',
          side: 'DEBIT',
          accountName: 'Groceries (old name)',
        }),
      ],
    })
    expect(resolveSpendingAccountName(expense, lookup)).toBe('Groceries (store)')
  })

  it('uses the LIABILITY debit split account for debt payments', () => {
    const debtPayment = txn({
      txnKind: 'DEBT_PAYMENT',
      splits: [
        split({ accountId: 'asset-checking', accountType: 'ASSET', side: 'CREDIT' }),
        split({
          accountId: 'liab-card',
          accountType: 'LIABILITY',
          side: 'DEBIT',
          accountName: 'Visa',
        }),
      ],
    })
    expect(resolveSpendingAccountName(debtPayment, lookup)).toBe('Credit card')
  })

  it('falls back to the split account name, then to null', () => {
    const notInStore = txn({
      txnKind: 'EXPENSE',
      splits: [
        split({ accountId: 'asset-checking', side: 'CREDIT' }),
        split({
          accountId: 'exp-archived',
          accountType: 'EXPENSE',
          side: 'DEBIT',
          accountName: 'Pets',
        }),
      ],
    })
    expect(resolveSpendingAccountName(notInStore, lookup)).toBe('Pets')

    const unknownAccount = txn({
      txnKind: 'EXPENSE',
      splits: [split({ accountId: 'exp-unknown', accountType: 'EXPENSE', side: 'DEBIT' })],
    })
    expect(resolveSpendingAccountName(unknownAccount, lookup)).toBeNull()
  })

  it('takes the first EXPENSE debit split when several exist', () => {
    const multi = txn({
      txnKind: 'EXPENSE',
      splits: [
        split({ accountId: 'a', accountType: 'EXPENSE', side: 'DEBIT', accountName: 'First' }),
        split({ accountId: 'b', accountType: 'EXPENSE', side: 'DEBIT', accountName: 'Second' }),
      ],
    })
    expect(resolveSpendingAccountName(multi, lookup)).toBe('First')
  })
})
