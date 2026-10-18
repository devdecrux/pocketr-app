import { describe, expect, it } from 'vitest'
import { totalsByCurrency } from '@/utils/currencyTotals'

describe('currencyTotals', () => {
  it('sums per currency with the primary currency first', () => {
    expect(
      totalsByCurrency(
        [
          { currency: 'USD', amountMinor: 500 },
          { currency: 'EUR', amountMinor: 100 },
          { currency: 'GBP', amountMinor: 50 },
          { currency: 'EUR', amountMinor: 200 },
        ],
        'EUR',
      ),
    ).toEqual([
      { currency: 'EUR', amountMinor: 300 },
      { currency: 'GBP', amountMinor: 50 },
      { currency: 'USD', amountMinor: 500 },
    ])
  })
})
