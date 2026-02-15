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
export function parseToMinor(
  inputString: string,
  minorUnit: number,
  allowNegative = false,
): number {
  const cleaned = inputString.trim().replace(/,/g, '.')

  const pattern = allowNegative ? /^-?\d+(\.\d*)?$/ : /^\d+(\.\d*)?$/
  if (cleaned === '' || !pattern.test(cleaned)) {
    return NaN
  }

  const isNegative = cleaned.startsWith('-')
  const normalized = isNegative ? cleaned.slice(1) : cleaned

  if (minorUnit === 0) {
    const parsed = Math.round(Number(normalized))
    return isNegative ? -parsed : parsed
  }

  const parts = normalized.split('.')
  const intPart = parts[0]
  const fracPart = (parts[1] ?? '').padEnd(minorUnit, '0').slice(0, minorUnit)
  const parsed = parseInt(intPart + fracPart, 10)
  return isNegative ? -parsed : parsed
}
