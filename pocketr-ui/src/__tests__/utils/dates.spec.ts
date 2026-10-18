import { describe, expect, it } from 'vitest'
import { CalendarDate } from '@internationalized/date'
import {
  calendarDateToIso,
  daysAgo,
  formatDate,
  formatIsoDate,
  formatPickerDate,
  formatPickerRange,
  formatTimestampDate,
  isoToCalendarDate,
  todayIso,
} from '@/utils/dates'

describe('dates', () => {
  it('todayIso uses the local calendar day, not UTC', () => {
    expect(todayIso(new Date(2026, 8, 5, 0, 30))).toBe('2026-09-05')
    expect(todayIso(new Date(2026, 11, 31, 23, 59))).toBe('2026-12-31')
  })

  it('formats dates, timestamps and ISO strings as DD/MM/YYYY in the local calendar', () => {
    expect(formatDate(new Date(2026, 8, 5))).toBe('05/09/2026')
    expect(formatTimestampDate(new Date(2026, 8, 12, 9, 30).toISOString())).toBe('12/09/2026')
    const blanks = [null, undefined, 'not a date']
    expect(blanks.map((timestamp) => [timestamp, formatTimestampDate(timestamp)])).toEqual(
      blanks.map((timestamp) => [timestamp, '']),
    )
    expect(formatIsoDate('2026-09-20')).toBe('20/09/2026')
    expect(formatIsoDate('soon')).toBe('soon')
  })

  it('round-trips ISO strings and treats unparsable ones as no date', () => {
    expect(calendarDateToIso(isoToCalendarDate('2026-09-20'))).toBe('2026-09-20')
    expect(isoToCalendarDate('nope')).toBeUndefined()
    expect(isoToCalendarDate('')).toBeUndefined()
    expect(calendarDateToIso(undefined)).toBeUndefined()
  })

  it('formats picker dates as DD/MM/YYYY and a reversed range in order', () => {
    expect(formatPickerDate(new CalendarDate(2026, 9, 3))).toBe('03/09/2026')
    expect(formatPickerRange(new CalendarDate(2026, 9, 30), new CalendarDate(2026, 9, 1))).toBe(
      '01/09/2026 – 30/09/2026',
    )
  })

  it('counts whole local days ago', () => {
    const now = new Date(2026, 8, 27, 18, 30)

    expect(daysAgo('2026-09-27', now)).toBe(0)
    expect(daysAgo('2026-09-26', now)).toBe(1)
    expect(daysAgo('2026-08-31', now)).toBe(27)
  })
})
