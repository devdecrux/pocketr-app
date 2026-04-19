import { describe, expect, it } from 'vitest'
import { getTxnPresentation } from '@/utils/txnPresentation'

describe('getTxnPresentation', () => {
  it('returns explicit presentation for opening balance', () => {
    expect(getTxnPresentation('OPENING_BALANCE')).toEqual({
      label: 'Opening Balance',
      badgeVariant: 'secondary',
      amountClass: 'text-[var(--app-transaction-amount-positive-fg)]',
      indicator: 'plus',
    })
  })

  it('returns explicit presentation for opening debt', () => {
    expect(getTxnPresentation('OPENING_DEBT')).toEqual({
      label: 'Opening Debt',
      badgeVariant: 'outline',
      amountClass: 'text-[var(--app-transaction-amount-warning-fg)]',
      indicator: 'plus',
    })
  })

  it('falls back to transfer presentation for unknown kinds', () => {
    expect(getTxnPresentation('SOMETHING_ELSE')).toEqual({
      label: 'Transfer',
      badgeVariant: 'secondary',
      amountClass: 'text-muted-foreground',
      indicator: 'transfer',
    })
  })
})
