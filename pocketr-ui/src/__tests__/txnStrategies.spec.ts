import { describe, expect, it } from 'vitest'
import {
  type ExpenseFields,
  expenseStrategy,
  type IncomeFields,
  incomeStrategy,
  type TransferFields,
  transferStrategy,
  type TxnModeContext
} from '@/utils/txnStrategies'

const ctx: TxnModeContext = { mode: 'INDIVIDUAL', householdId: null }
const MSG = 'Please fill in all required fields and enter a positive amount.'

// --- Expense ---

function validExpense(): ExpenseFields {
  return {
    date: '2026-02-24',
    payFrom: 'acc-1',
    account: 'acc-2',
    amount: 1000,
    currency: 'USD',
    description: 'Groceries',
    categoryTagId: 'cat-1',
  }
}

describe('expenseStrategy.validate', () => {
  it('returns null for valid fields', () => {
    expect(expenseStrategy.validate(validExpense())).toBeNull()
  })

  it.each([
    ['payFrom', { payFrom: '' }],
    ['account', { account: '' }],
    ['amount zero', { amount: 0 }],
    ['amount negative', { amount: -1 }],
    ['description empty', { description: '' }],
    ['description whitespace', { description: '   ' }],
  ])('rejects when %s is invalid', (_label, override) => {
    expect(expenseStrategy.validate({ ...validExpense(), ...override })).toBe(MSG)
  })
})

describe('expenseStrategy.buildRequest', () => {
  it('builds correct splits', () => {
    const req = expenseStrategy.buildRequest(ctx, validExpense())
    expect(req.mode).toBe('INDIVIDUAL')
    expect(req.currency).toBe('USD')
    expect(req.description).toBe('Groceries')
    expect(req.splits).toHaveLength(2)
    expect(req.splits[0]).toEqual({ accountId: 'acc-1', side: 'CREDIT', amountMinor: 1000 })
    expect(req.splits[1]).toMatchObject({
      accountId: 'acc-2',
      side: 'DEBIT',
      amountMinor: 1000,
      categoryTagId: 'cat-1',
    })
  })

  it('trims description', () => {
    const req = expenseStrategy.buildRequest(ctx, {
      ...validExpense(),
      description: '  Lunch  ',
    })
    expect(req.description).toBe('Lunch')
  })
})

// --- Income ---

function validIncome(): IncomeFields {
  return {
    date: '2026-02-24',
    deposit: 'acc-1',
    account: 'acc-2',
    amount: 5000,
    currency: 'EUR',
    description: 'Salary',
  }
}

describe('incomeStrategy.validate', () => {
  it('returns null for valid fields', () => {
    expect(incomeStrategy.validate(validIncome())).toBeNull()
  })

  it.each([
    ['deposit', { deposit: '' }],
    ['account', { account: '' }],
    ['amount zero', { amount: 0 }],
    ['description empty', { description: '' }],
  ])('rejects when %s is invalid', (_label, override) => {
    expect(incomeStrategy.validate({ ...validIncome(), ...override })).toBe(MSG)
  })
})

describe('incomeStrategy.buildRequest', () => {
  it('builds correct splits (DEBIT deposit, CREDIT income account)', () => {
    const req = incomeStrategy.buildRequest(ctx, validIncome())
    expect(req.splits[0]).toEqual({ accountId: 'acc-1', side: 'DEBIT', amountMinor: 5000 })
    expect(req.splits[1]).toEqual({ accountId: 'acc-2', side: 'CREDIT', amountMinor: 5000 })
  })
})

// --- Transfer ---

function validTransfer(): TransferFields {
  return {
    date: '2026-02-24',
    from: 'acc-1',
    to: 'acc-2',
    amount: 2000,
    currency: 'USD',
    description: 'Savings transfer',
  }
}

describe('transferStrategy.validate', () => {
  it('returns null for valid fields', () => {
    expect(transferStrategy.validate(validTransfer())).toBeNull()
  })

  it.each([
    ['from', { from: '' }],
    ['to', { to: '' }],
    ['amount zero', { amount: 0 }],
    ['description empty', { description: '' }],
  ])('rejects when %s is invalid', (_label, override) => {
    expect(transferStrategy.validate({ ...validTransfer(), ...override })).toBe(MSG)
  })
})

describe('transferStrategy.buildRequest', () => {
  it('builds correct splits (CREDIT from, DEBIT to)', () => {
    const req = transferStrategy.buildRequest(ctx, validTransfer())
    expect(req.splits[0]).toEqual({ accountId: 'acc-1', side: 'CREDIT', amountMinor: 2000 })
    expect(req.splits[1]).toEqual({ accountId: 'acc-2', side: 'DEBIT', amountMinor: 2000 })
  })

  it('passes household context through', () => {
    const householdCtx: TxnModeContext = { mode: 'HOUSEHOLD', householdId: 'hh-1' }
    const req = transferStrategy.buildRequest(householdCtx, validTransfer())
    expect(req.mode).toBe('HOUSEHOLD')
    expect(req.householdId).toBe('hh-1')
  })
})
