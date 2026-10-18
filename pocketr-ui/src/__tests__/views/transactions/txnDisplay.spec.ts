import { describe, expect, it } from 'vitest'
import { formatSplitAmount, formatTxnDisplayAmount } from '@/views/transactions/txnDisplay'
import { buildSplit, buildTxn } from '@/__tests__/support/fixtures'

const minorUnit = (currency: string) => (currency === 'JPY' ? 0 : 2)

describe('transaction display formatting', () => {
  it('totals a transaction from its source-currency split amounts', () => {
    const transaction = buildTxn({
      currency: 'USD',
      splits: [
        buildSplit({ accountCurrency: 'USD', side: 'CREDIT', amountMinor: 1000 }),
        buildSplit({
          accountCurrency: 'EUR',
          side: 'DEBIT',
          amountMinor: 920,
          exchangeRate: '0.92',
        }),
      ],
    })

    expect(formatTxnDisplayAmount(transaction, minorUnit)).toBe('$10.00')
  })

  it('does not sum converted account-currency splits as if they were the source currency', () => {
    const transaction = buildTxn({
      currency: 'USD',
      splits: [
        buildSplit({
          accountCurrency: 'EUR',
          side: 'DEBIT',
          amountMinor: 920,
          exchangeRate: '0.92',
        }),
        buildSplit({
          accountCurrency: 'BGN',
          side: 'CREDIT',
          amountMinor: 1800,
          exchangeRate: '1.8',
        }),
      ],
    })

    expect(formatTxnDisplayAmount(transaction, minorUnit)).toBe('Mixed currencies')
  })

  it('formats a split amount in its own account currency, signed by side', () => {
    const split = buildSplit({ accountCurrency: 'EUR', side: 'DEBIT', amountMinor: 920 })

    expect(formatSplitAmount(split, minorUnit)).toBe('+€9.20')
  })
})
