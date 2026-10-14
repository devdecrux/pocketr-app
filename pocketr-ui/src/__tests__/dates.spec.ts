import { describe, expect, it } from 'vitest'
import { formatDate, formatIsoDate, formatTimestampDate } from '@/utils/dates'

describe('date display helpers', () => {
  it('formats a Date as DD/MM/YYYY in the local calendar', () => {
    expect(formatDate(new Date(2026, 8, 5))).toBe('05/09/2026')
  })

  it('formats an ISO timestamp as the local calendar date', () => {
    const local = new Date(2026, 8, 12, 9, 30)
    expect(formatTimestampDate(local.toISOString())).toBe('12/09/2026')
  })

  it('shows nothing for a missing or unparsable timestamp', () => {
    expect(formatTimestampDate(null)).toBe('')
    expect(formatTimestampDate(undefined)).toBe('')
    expect(formatTimestampDate('not a date')).toBe('')
  })

  it('formats an ISO date and keeps an unparsable value as it is', () => {
    expect(formatIsoDate('2026-09-20')).toBe('20/09/2026')
    expect(formatIsoDate('soon')).toBe('soon')
  })
})
