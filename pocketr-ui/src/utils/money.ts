/**
 * Format a minor-unit integer amount to a display string.
 * e.g. formatMinor(4500, 'EUR', 2) => '45.00'
 */
export function formatMinor(amountMinor: number, currencyCode: string, minorUnit: number): string {
  if (minorUnit === 0) {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: currencyCode,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amountMinor)
  }

  const divisor = 10 ** minorUnit
  const value = amountMinor / divisor

  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currencyCode,
    minimumFractionDigits: minorUnit,
    maximumFractionDigits: minorUnit,
  }).format(value)
}

/**
 * Format a minor-unit integer amount to a plain number string (no currency symbol).
 * e.g. formatMinorPlain(4500, 2) => '45.00'
 */
export function formatMinorPlain(amountMinor: number, minorUnit: number): string {
  if (minorUnit === 0) return String(amountMinor)
  const divisor = 10 ** minorUnit
  return (amountMinor / divisor).toFixed(minorUnit)
}

/**
 * Parse a display string to a minor-unit integer.
 * e.g. parseToMinor('45.00', 2) => 4500
 * Handles comma as decimal separator.
 * Returns NaN if the input is not a valid number.
 */
export function parseToMinor(inputString: string, minorUnit: number): number {
  const cleaned = inputString.trim().replace(/,/g, '.')

  // Reject negative values — amount_minor must be > 0, side (DEBIT/CREDIT) determines direction
  if (cleaned === '' || !/^\d+(\.\d*)?$/.test(cleaned)) {
    return NaN
  }

  if (minorUnit === 0) {
    return Math.round(Number(cleaned))
  }

  const parts = cleaned.split('.')
  const intPart = parts[0]
  const fracPart = (parts[1] ?? '').padEnd(minorUnit, '0').slice(0, minorUnit)

  return parseInt(intPart + fracPart, 10)
}
