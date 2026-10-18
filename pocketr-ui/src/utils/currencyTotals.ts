import { PRIMARY_CURRENCY } from '@/utils/money'

/** An amount in minor units of one currency. */
export interface CurrencyAmount {
  currency: string
  amountMinor: number
}

/** The primary currency when it is in use, else the first one in use. */
export function pickPrimaryCurrency(currencies: readonly string[]): string {
  if (currencies.includes(PRIMARY_CURRENCY)) return PRIMARY_CURRENCY
  return [...currencies].sort((a, b) => a.localeCompare(b))[0] ?? PRIMARY_CURRENCY
}

/** Sums amounts per currency; the primary currency comes first, the others alphabetically. */
export function totalsByCurrency(
  amounts: readonly CurrencyAmount[],
  primary: string,
): CurrencyAmount[] {
  const totals = new Map<string, number>()
  for (const { currency, amountMinor } of amounts) {
    totals.set(currency, (totals.get(currency) ?? 0) + amountMinor)
  }
  return [...totals.entries()]
    .map(([currency, amountMinor]) => ({ currency, amountMinor }))
    .sort((a, b) => {
      if (a.currency === primary) return -1
      if (b.currency === primary) return 1
      return a.currency.localeCompare(b.currency)
    })
}
