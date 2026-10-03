import { describe, expect, it } from 'vitest'
import { getAccountAppearance } from '@/utils/accountAppearance'
import { pickPrimaryCurrency, totalsByCurrency } from '@/utils/currencyTotals'

describe('currencyTotals', () => {
  it('prefers EUR, else the first currency alphabetically', () => {
    expect(pickPrimaryCurrency(['USD', 'EUR'])).toBe('EUR')
    expect(pickPrimaryCurrency(['USD', 'GBP'])).toBe('GBP')
    expect(pickPrimaryCurrency([])).toBe('EUR')
  })

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

describe('getAccountAppearance', () => {
  it('uses the owner-approved colour per account type and one icon per type', () => {
    expect(getAccountAppearance('INCOME').badgeColor).toBe('success')
    expect(getAccountAppearance('EXPENSE').badgeColor).toBe('error')
    expect(getAccountAppearance('LIABILITY').badgeColor).toBe('warning')
    expect(getAccountAppearance('ASSET').badgeColor).toBe('primary')
    const icons = (['ASSET', 'EXPENSE', 'INCOME', 'LIABILITY'] as const).map(
      (type) => getAccountAppearance(type).icon,
    )
    expect(new Set(icons).size).toBe(4)
  })
})
