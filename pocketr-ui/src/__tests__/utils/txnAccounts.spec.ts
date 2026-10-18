import { describe, expect, it } from 'vitest'
import { resolveSpendingAccountName } from '@/utils/txnAccounts'
import { buildSplit, buildTxn } from '@/__tests__/support/fixtures'

const accounts = new Map([
  ['exp-groceries', { name: 'Groceries (store)', type: 'EXPENSE' as const }],
  ['liab-card', { name: 'Credit card', type: 'LIABILITY' as const }],
  ['asset-checking', { name: 'Checking', type: 'ASSET' as const }],
])
const lookup = (id: string) => accounts.get(id)

const paidFrom = buildSplit({ accountId: 'asset-checking', accountType: 'ASSET', side: 'CREDIT' })

describe('resolveSpendingAccountName', () => {
  it('prefers the current account name for the debit split of an expense or debt payment', () => {
    const expense = buildTxn({
      txnKind: 'EXPENSE',
      splits: [
        paidFrom,
        buildSplit({
          accountId: 'exp-groceries',
          accountType: 'EXPENSE',
          side: 'DEBIT',
          accountName: 'Groceries (old name)',
        }),
      ],
    })
    const debtPayment = buildTxn({
      txnKind: 'DEBT_PAYMENT',
      splits: [
        paidFrom,
        buildSplit({
          accountId: 'liab-card',
          accountType: 'LIABILITY',
          side: 'DEBIT',
          accountName: 'Visa',
        }),
      ],
    })

    expect(resolveSpendingAccountName(expense, lookup)).toBe('Groceries (store)')
    expect(resolveSpendingAccountName(debtPayment, lookup)).toBe('Credit card')
  })

  it('falls back to the name stored on the split, then to null', () => {
    const notInStore = buildTxn({
      splits: [
        paidFrom,
        buildSplit({
          accountId: 'exp-archived',
          accountType: 'EXPENSE',
          side: 'DEBIT',
          accountName: 'Pets',
        }),
      ],
    })
    const unknown = buildTxn({
      splits: [
        buildSplit({
          accountId: 'exp-unknown',
          accountType: 'EXPENSE',
          side: 'DEBIT',
          accountName: undefined,
        }),
      ],
    })

    expect(resolveSpendingAccountName(notInStore, lookup)).toBe('Pets')
    expect(resolveSpendingAccountName(unknown, lookup)).toBeNull()
  })

  it('takes the first expense debit split when several exist', () => {
    const multi = buildTxn({
      splits: [
        buildSplit({ accountId: 'a', accountType: 'EXPENSE', side: 'DEBIT', accountName: 'First' }),
        buildSplit({
          accountId: 'b',
          accountType: 'EXPENSE',
          side: 'DEBIT',
          accountName: 'Second',
        }),
      ],
    })

    expect(resolveSpendingAccountName(multi, lookup)).toBe('First')
  })
})
