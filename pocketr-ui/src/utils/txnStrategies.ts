import type { CreateTxnRequest, SplitSide } from '@/types/ledger'

export interface TxnModeContext {
  mode: 'INDIVIDUAL' | 'HOUSEHOLD'
  householdId: string | null
}

export interface ExpenseFields {
  date: string
  payFrom: string
  account: string
  amount: number
  currency: string
  description: string
  categoryTagId: string | null
  memo: string
}

export interface IncomeFields {
  date: string
  deposit: string
  account: string
  amount: number
  currency: string
  description: string
}

export interface TransferFields {
  date: string
  from: string
  to: string
  amount: number
  currency: string
  description: string
}

const VALIDATION_MSG = 'Please fill in all required fields and enter a positive amount.'

export interface TxnTabStrategy<F> {
  validate(fields: F): string | null
  buildRequest(ctx: TxnModeContext, fields: F): CreateTxnRequest
}

export const expenseStrategy: TxnTabStrategy<ExpenseFields> = {
  validate(f) {
    if (!f.payFrom || !f.account || f.amount <= 0 || !f.description.trim()) {
      return VALIDATION_MSG
    }
    return null
  },
  buildRequest(ctx, f) {
    return {
      mode: ctx.mode,
      householdId: ctx.householdId,
      txnDate: f.date,
      currency: f.currency,
      description: f.description.trim(),
      splits: [
        {
          accountId: f.payFrom,
          side: 'CREDIT' as SplitSide,
          amountMinor: f.amount,
        },
        {
          accountId: f.account,
          side: 'DEBIT' as SplitSide,
          amountMinor: f.amount,
          categoryTagId: f.categoryTagId,
          memo: f.memo.trim() || null,
        },
      ],
    }
  },
}

export const incomeStrategy: TxnTabStrategy<IncomeFields> = {
  validate(f) {
    if (!f.deposit || !f.account || f.amount <= 0 || !f.description.trim()) {
      return VALIDATION_MSG
    }
    return null
  },
  buildRequest(ctx, f) {
    return {
      mode: ctx.mode,
      householdId: ctx.householdId,
      txnDate: f.date,
      currency: f.currency,
      description: f.description.trim(),
      splits: [
        {
          accountId: f.deposit,
          side: 'DEBIT' as SplitSide,
          amountMinor: f.amount,
        },
        {
          accountId: f.account,
          side: 'CREDIT' as SplitSide,
          amountMinor: f.amount,
        },
      ],
    }
  },
}

export const transferStrategy: TxnTabStrategy<TransferFields> = {
  validate(f) {
    if (!f.from || !f.to || f.amount <= 0 || !f.description.trim()) {
      return VALIDATION_MSG
    }
    return null
  },
  buildRequest(ctx, f) {
    return {
      mode: ctx.mode,
      householdId: ctx.householdId,
      txnDate: f.date,
      currency: f.currency,
      description: f.description.trim(),
      splits: [
        {
          accountId: f.from,
          side: 'CREDIT' as SplitSide,
          amountMinor: f.amount,
        },
        {
          accountId: f.to,
          side: 'DEBIT' as SplitSide,
          amountMinor: f.amount,
        },
      ],
    }
  },
}
