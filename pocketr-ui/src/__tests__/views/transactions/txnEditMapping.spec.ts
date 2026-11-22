import { beforeEach, describe, expect, it } from 'vitest'
import { useAccountStore } from '@/stores/account'
import type { CreateTxnRequest, LedgerTxn } from '@/types/ledger'
import { ACCOUNTS, buildSplit, buildTxn } from '@/__tests__/support/fixtures'
import { isTxnEditable, prefillFromTxn } from '@/views/transactions/txnEditMapping'
import { useTransactionDraft } from '@/views/transactions/useTransactionDraft'

const BASE = {
  mode: 'INDIVIDUAL',
  householdId: null,
  txnDate: '2026-09-20',
  currency: 'EUR',
  description: 'Grocery market',
} as const

/** Editing `txn` and saving without changes: the request the form would send. */
function requestOf(txn: LedgerTxn): CreateTxnRequest | undefined {
  return useTransactionDraft(txn).strategyResult.value.request
}

describe('editing a transaction', () => {
  beforeEach(() => {
    useAccountStore().accounts = ACCOUNTS
  })

  const roundTrips: [string, LedgerTxn, CreateTxnRequest['splits']][] = [
    [
      'expense',
      buildTxn(),
      [
        { accountId: 'daily', side: 'CREDIT', amountMinor: 4680 },
        { accountId: 'living', side: 'DEBIT', amountMinor: 4680, categoryTagId: 'c1' },
      ],
    ],
    [
      'expense without a category',
      buildTxn({
        splits: [
          buildSplit({ accountId: 'daily', side: 'CREDIT' }),
          buildSplit({ accountId: 'living', side: 'DEBIT' }),
        ],
      }),
      [
        { accountId: 'daily', side: 'CREDIT', amountMinor: 4680 },
        { accountId: 'living', side: 'DEBIT', amountMinor: 4680, categoryTagId: null },
      ],
    ],
    [
      'income',
      buildTxn({
        txnKind: 'INCOME',
        splits: [
          buildSplit({ accountId: 'salary', side: 'CREDIT' }),
          buildSplit({ accountId: 'daily', side: 'DEBIT' }),
        ],
      }),
      [
        { accountId: 'daily', side: 'DEBIT', amountMinor: 4680 },
        { accountId: 'salary', side: 'CREDIT', amountMinor: 4680 },
      ],
    ],
    [
      'transfer',
      buildTxn({
        txnKind: 'TRANSFER',
        splits: [
          buildSplit({ accountId: 'daily', side: 'CREDIT' }),
          buildSplit({ accountId: 'savings', side: 'DEBIT' }),
        ],
      }),
      [
        { accountId: 'daily', side: 'CREDIT', amountMinor: 4680 },
        { accountId: 'savings', side: 'DEBIT', amountMinor: 4680 },
      ],
    ],
    [
      'debt payment',
      buildTxn({
        txnKind: 'DEBT_PAYMENT',
        splits: [
          buildSplit({ accountId: 'daily', side: 'CREDIT' }),
          buildSplit({ accountId: 'card', side: 'DEBIT' }),
        ],
      }),
      [
        { accountId: 'daily', side: 'CREDIT', amountMinor: 4680 },
        { accountId: 'card', side: 'DEBIT', amountMinor: 4680 },
      ],
    ],
  ]

  it.each(roundTrips)(
    'rebuilds the original %s request from the stored transaction',
    (_, txn, splits) => {
      expect(requestOf(txn)).toEqual({ ...BASE, splits })
    },
  )

  it('takes the amount in the transaction currency, not the other account currency', () => {
    // 10.00 EUR leaves Daily account and arrives as 11.50 USD on the USD cash account.
    const crossCurrency = buildTxn({
      txnKind: 'TRANSFER',
      splits: [
        buildSplit({ accountId: 'daily', side: 'CREDIT', amountMinor: 1000 }),
        buildSplit({
          accountId: 'usd',
          side: 'DEBIT',
          amountMinor: 1150,
          accountCurrency: 'USD',
          exchangeRate: '1.15',
        }),
      ],
    })
    expect(requestOf(crossCurrency)).toMatchObject({
      currency: 'EUR',
      splits: [
        { accountId: 'daily', side: 'CREDIT', amountMinor: 1000 },
        { accountId: 'usd', side: 'DEBIT', amountMinor: 1000 },
      ],
    })

    // Same transfer the other way: the transaction currency is USD, carried by the debit split.
    const fromUsd = buildTxn({
      txnKind: 'TRANSFER',
      currency: 'USD',
      splits: [
        buildSplit({
          accountId: 'usd',
          side: 'CREDIT',
          amountMinor: 1150,
          accountCurrency: 'USD',
        }),
        buildSplit({ accountId: 'daily', side: 'DEBIT', amountMinor: 1000, exchangeRate: '0.87' }),
      ],
    })
    expect(requestOf(fromUsd)?.splits.map((split) => split.amountMinor)).toEqual([1150, 1150])
  })

  it('starts on the tab of the kind and locks the type for an edit only', () => {
    const transfer = buildTxn({
      txnKind: 'TRANSFER',
      splits: [
        buildSplit({ accountId: 'daily', side: 'CREDIT' }),
        buildSplit({ accountId: 'savings', side: 'DEBIT' }),
      ],
    })
    const editing = useTransactionDraft(transfer)
    expect(editing.activeTab.value).toBe('transfer')
    expect(editing.isEditing).toBe(true)

    const creating = useTransactionDraft()
    expect(creating.activeTab.value).toBe('expense')
    expect(creating.isEditing).toBe(false)
    expect(creating.isValid.value).toBe(false)
  })

  it('does not offer an edit for what the form cannot represent', () => {
    const opening = buildTxn({
      txnKind: 'OPENING_BALANCE',
      splits: [
        buildSplit({ accountId: 'daily', side: 'DEBIT' }),
        buildSplit({ accountId: 'salary', side: 'CREDIT' }),
      ],
    })
    const multiSplit = buildTxn({
      splits: [
        buildSplit({ accountId: 'daily', side: 'CREDIT', amountMinor: 3000 }),
        buildSplit({ accountId: 'savings', side: 'CREDIT', amountMinor: 1680 }),
        buildSplit({ accountId: 'living', side: 'DEBIT', amountMinor: 4680 }),
      ],
    })

    for (const txn of [opening, multiSplit, buildTxn({ txnKind: 'OPENING_DEBT' })]) {
      expect(isTxnEditable(txn)).toBe(false)
      expect(prefillFromTxn(txn)).toBeNull()
    }
    expect(isTxnEditable(buildTxn())).toBe(true)
  })
})
