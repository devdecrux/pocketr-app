import { describe, expect, it } from 'vitest'
import {
  debtPaymentStrategy,
  expenseStrategy,
  incomeStrategy,
  transferStrategy,
  type TxnModeContext,
} from '@/views/transactions/txnStrategies'

const ctx: TxnModeContext = { mode: 'INDIVIDUAL', householdId: null }
const REQUIRED_MESSAGE = 'Please fill in all required fields and enter a positive amount.'

interface StrategyCase {
  name: string
  strategy: {
    validate: (fields: never) => string | null
    buildRequest: (
      ctx: TxnModeContext,
      fields: never,
    ) => ReturnType<typeof expenseStrategy.buildRequest>
  }
  valid: Record<string, unknown>
  /** Fields that must be filled; the strategy rejects the draft when one is empty. */
  required: string[]
  /** The two splits as [accountId, side], in request order. */
  splits: [string, 'DEBIT' | 'CREDIT'][]
}

const CASES: StrategyCase[] = [
  {
    name: 'expense',
    strategy: expenseStrategy as never,
    valid: {
      date: '2026-02-24',
      payFrom: 'acc-1',
      account: 'acc-2',
      amount: 1000,
      currency: 'USD',
      description: 'Groceries',
      categoryTagId: 'cat-1',
    },
    required: ['payFrom', 'account'],
    splits: [
      ['acc-1', 'CREDIT'],
      ['acc-2', 'DEBIT'],
    ],
  },
  {
    name: 'income',
    strategy: incomeStrategy as never,
    valid: {
      date: '2026-02-24',
      deposit: 'acc-1',
      account: 'acc-2',
      amount: 5000,
      currency: 'EUR',
      description: 'Salary',
    },
    required: ['deposit', 'account'],
    splits: [
      ['acc-1', 'DEBIT'],
      ['acc-2', 'CREDIT'],
    ],
  },
  {
    name: 'transfer',
    strategy: transferStrategy as never,
    valid: {
      date: '2026-02-24',
      from: 'acc-1',
      to: 'acc-2',
      amount: 2000,
      currency: 'USD',
      description: 'Savings transfer',
    },
    required: ['from', 'to'],
    splits: [
      ['acc-1', 'CREDIT'],
      ['acc-2', 'DEBIT'],
    ],
  },
  {
    name: 'debt payment',
    strategy: debtPaymentStrategy as never,
    valid: {
      date: '2026-02-24',
      payFrom: 'acc-1',
      liabilityAccount: 'acc-2',
      amount: 1500,
      currency: 'EUR',
      description: 'Car lease payment',
    },
    required: ['payFrom', 'liabilityAccount'],
    splits: [
      ['acc-1', 'CREDIT'],
      ['acc-2', 'DEBIT'],
    ],
  },
]

describe.each(CASES)('$name strategy', ({ strategy, valid, required, splits }) => {
  it('accepts valid fields, with or without a description', () => {
    expect(strategy.validate(valid as never)).toBeNull()
    expect(strategy.validate({ ...valid, description: '' } as never)).toBeNull()
  })

  it('rejects a draft with a missing account or a non-positive amount', () => {
    const rejected = [
      ...required.map((field) => ({ ...valid, [field]: '' })),
      { ...valid, amount: 0 },
      { ...valid, amount: -1 },
    ]

    for (const draft of rejected) {
      expect(strategy.validate(draft as never)).toBe(REQUIRED_MESSAGE)
    }
  })

  it('debits and credits the right accounts in source-currency minor units, trims the description and passes the household through', () => {
    const request = strategy.buildRequest({ mode: 'HOUSEHOLD', householdId: 'hh-1' }, {
      ...valid,
      amount: 1234,
      description: '  Lunch  ',
    } as never)

    expect(request.currency).toBe(valid.currency)
    expect(request.splits.map((split) => [split.accountId, split.side])).toEqual(splits)
    expect(request.splits.map((split) => split.amountMinor)).toEqual([1234, 1234])
    expect(request.description).toBe('Lunch')
    expect(request.mode).toBe('HOUSEHOLD')
    expect(request.householdId).toBe('hh-1')
  })
})

describe('expense strategy category', () => {
  it('puts the category on the expense account split only', () => {
    const request = expenseStrategy.buildRequest(ctx, {
      date: '2026-02-24',
      payFrom: 'acc-1',
      account: 'acc-2',
      amount: 1000,
      currency: 'USD',
      description: 'Groceries',
      categoryTagId: 'cat-1',
    })

    expect(request.splits[0]).toEqual({ accountId: 'acc-1', side: 'CREDIT', amountMinor: 1000 })
    expect(request.splits[1]).toMatchObject({ accountId: 'acc-2', categoryTagId: 'cat-1' })
  })
})
