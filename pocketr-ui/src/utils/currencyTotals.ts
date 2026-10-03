export interface CurrencyAmount {
  currency: string
  amountMinor: number
}

/** The currency the summary shows large: EUR as on the dashboard, else the first one in use. */
export const PRIMARY_CURRENCY = 'EUR'

export function pickPrimaryCurrency(currencies: readonly string[]): string {
  if (currencies.includes(PRIMARY_CURRENCY)) return PRIMARY_CURRENCY
  return [...currencies].sort()[0] ?? PRIMARY_CURRENCY
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
    .sort((a, b) =>
      a.currency === primary
        ? -1
        : b.currency === primary
          ? 1
          : a.currency.localeCompare(b.currency),
    )
}
